import { apiFetch, setSessionExpiredHandler } from '@/lib/api';
import { ApiError } from '@/lib/api-error';
import { sessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => {}),
  deleteItemAsync: jest.fn(async () => {}),
}));

type Call = { path: string; method: string; token: string | null; body: unknown };
type Reply = { status: number; body?: unknown; html?: string };
type Handler = (call: Call) => Reply | Promise<Reply>;

let handler: Handler;
let calls: Call[];
const expired = jest.fn();

const AUTH_REQUIRED: Reply = {
  status: 401,
  body: { success: false, error: { code: 'AUTH_REQUIRED', message: '로그인이 필요합니다.' } },
};
const ok = (data: unknown): Reply => ({ status: 200, body: { success: true, data } });
const refreshes = () => calls.filter((c) => c.path === '/auth/refresh');

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (e) {
    expect(e).toBeInstanceOf(ApiError);
    return e as ApiError;
  }
  throw new Error('실패해야 하는 호출이 성공했다');
}

beforeEach(async () => {
  calls = [];
  expired.mockReset();
  setSessionExpiredHandler(expired);
  await sessionStore.signIn(tokens('1'));
  globalThis.fetch = jest.fn(async (url: string, init: RequestInit) => {
    const headers = init.headers as Record<string, string>;
    const call: Call = {
      path: url.replace('https://api.test/api/v1', ''),
      method: init.method ?? 'GET',
      token: headers.Authorization ?? null,
      body: init.body ? JSON.parse(init.body as string) : undefined,
    };
    calls.push(call);
    const reply = await handler(call);
    return new Response(reply.html ?? (reply.body ? JSON.stringify(reply.body) : null), {
      status: reply.status,
    });
  }) as unknown as typeof fetch;
});

const expiring: Handler = (call) => {
  if (call.path === '/auth/refresh') return ok(tokens('2'));
  return call.token === 'Bearer A2' ? ok({ path: call.path }) : AUTH_REQUIRED;
};

it('sends the bearer token and unwraps data', async () => {
  handler = () => ok({ id: 7 });
  await expect(apiFetch('/meetups', { method: 'POST', body: { a: 1 } })).resolves.toEqual({
    id: 7,
  });
  expect(calls).toEqual([{ path: '/meetups', method: 'POST', token: 'Bearer A1', body: { a: 1 } }]);
});

it('omits the bearer token when auth is false', async () => {
  handler = () => ok({});
  await apiFetch('/app-settings', { auth: false });
  expect(calls[0].token).toBeNull();
});

it('throws ApiError with code, message and fields', async () => {
  const fields = [{ field: 'title', reason: '비어 있을 수 없습니다.' }];
  handler = () => ({
    status: 400,
    body: { success: false, error: { code: 'COMMON_VALIDATION_FAILED', message: '확인', fields } },
  });
  expect(await failure(apiFetch('/x'))).toMatchObject({
    code: 'COMMON_VALIDATION_FAILED',
    message: '확인',
    status: 400,
    fields,
  });
});

it('returns undefined for 204', async () => {
  handler = () => ({ status: 204 });
  await expect(apiFetch('/push-tokens', { method: 'PUT', body: {} })).resolves.toBeUndefined();
});

it('refreshes once and retries on AUTH_REQUIRED', async () => {
  handler = expiring;
  await expect(apiFetch('/users/me')).resolves.toEqual({ path: '/users/me' });
  expect(calls.map((c) => [c.path, c.token])).toEqual([
    ['/users/me', 'Bearer A1'],
    ['/auth/refresh', null],
    ['/users/me', 'Bearer A2'],
  ]);
  expect(calls[1].body).toEqual({ refreshToken: 'R1' });
  expect(sessionStore.getState()).toEqual({ status: 'authenticated', tokens: tokens('2') });
});

it('sends a single refresh for concurrent 401s', async () => {
  handler = expiring;
  const results = await Promise.all([apiFetch('/a'), apiFetch('/b'), apiFetch('/c')]);
  expect(results).toEqual([{ path: '/a' }, { path: '/b' }, { path: '/c' }]);
  expect(refreshes()).toHaveLength(1);
});

it('retries with the stored token when another call already refreshed', async () => {
  const late = deferred<Reply>();
  handler = (call) => {
    if (call.path === '/slow' && call.token === 'Bearer A1') return late.promise;
    return expiring(call);
  };
  const slow = apiFetch('/slow');
  await apiFetch('/fast');
  late.resolve(AUTH_REQUIRED);
  await expect(slow).resolves.toEqual({ path: '/slow' });
  expect(refreshes()).toHaveLength(1);
});

it('does not refresh on AUTH_INVALID_SOCIAL_TOKEN', async () => {
  handler = () => ({
    status: 401,
    body: { success: false, error: { code: 'AUTH_INVALID_SOCIAL_TOKEN', message: '실패' } },
  });
  expect((await failure(apiFetch('/x'))).code).toBe('AUTH_INVALID_SOCIAL_TOKEN');
  expect(refreshes()).toHaveLength(0);
});

it('does not refresh when there is no session', async () => {
  await sessionStore.signOut();
  handler = () => AUTH_REQUIRED;
  expect((await failure(apiFetch('/x'))).code).toBe('AUTH_REQUIRED');
  expect(refreshes()).toHaveLength(0);
  expect(expired).not.toHaveBeenCalled();
});

it('signs out and calls the expired handler once when refresh returns AUTH_REQUIRED', async () => {
  handler = () => AUTH_REQUIRED;
  const errors = await Promise.all([failure(apiFetch('/a')), failure(apiFetch('/b'))]);
  expect(errors.map((e) => e.code)).toEqual(['AUTH_REQUIRED', 'AUTH_REQUIRED']);
  expect(sessionStore.getState()).toEqual({ status: 'guest' });
  expect(expired).toHaveBeenCalledTimes(1);
});

it('keeps the session when refresh fails on the network', async () => {
  handler = (call) => {
    if (call.path === '/auth/refresh') throw new TypeError('Network request failed');
    return AUTH_REQUIRED;
  };
  expect((await failure(apiFetch('/x'))).code).toBe('CLIENT_NETWORK');
  expect(sessionStore.getState()).toEqual({ status: 'authenticated', tokens: tokens('1') });
  expect(expired).not.toHaveBeenCalled();
});

it('keeps the session when the server returns HTML', async () => {
  handler = (call) =>
    call.path === '/auth/refresh'
      ? { status: 502, html: '<html>Bad gateway</html>' }
      : AUTH_REQUIRED;
  const error = await failure(apiFetch('/x'));
  expect(error).toMatchObject({ code: 'CLIENT_BAD_RESPONSE', status: 502 });
  expect(sessionStore.getState()).toEqual({ status: 'authenticated', tokens: tokens('1') });
});

it('can refresh again after a failed refresh', async () => {
  let attempts = 0;
  handler = (call) => {
    if (call.path === '/auth/refresh' && (attempts += 1) === 1) throw new TypeError('offline');
    return expiring(call);
  };
  await failure(apiFetch('/x'));
  await expect(apiFetch('/x')).resolves.toEqual({ path: '/x' });
});

it('ignores a refresh response that arrives after logout', async () => {
  const refresh = deferred<Reply>();
  handler = (call) => (call.path === '/auth/refresh' ? refresh.promise : AUTH_REQUIRED);
  const pending = failure(apiFetch('/x'));
  while (refreshes().length === 0) await Promise.resolve();
  await sessionStore.signOut();
  refresh.resolve(ok(tokens('2')));
  expect((await pending).code).toBe('AUTH_REQUIRED');
  expect(sessionStore.getState()).toEqual({ status: 'guest' });
  expect(calls.filter((c) => c.path === '/x')).toHaveLength(1);
});

it('does not sign out a new account when the old account refresh is rejected', async () => {
  const refresh = deferred<Reply>();
  handler = (call) => (call.path === '/auth/refresh' ? refresh.promise : AUTH_REQUIRED);
  const pending = failure(apiFetch('/x'));
  while (refreshes().length === 0) await Promise.resolve();
  await sessionStore.signIn(tokens('9'));
  refresh.resolve(AUTH_REQUIRED);
  await pending;
  expect(sessionStore.getState()).toEqual({ status: 'authenticated', tokens: tokens('9') });
  expect(expired).not.toHaveBeenCalled();
});

it('does not retry more than once when the retried call is also 401', async () => {
  handler = (call) => (call.path === '/auth/refresh' ? ok(tokens('2')) : AUTH_REQUIRED);
  expect((await failure(apiFetch('/x'))).code).toBe('AUTH_REQUIRED');
  expect(calls).toHaveLength(3);
});

it('times out after 15 seconds with CLIENT_NETWORK', async () => {
  jest.useFakeTimers();
  try {
    globalThis.fetch = jest.fn(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new Error('aborted')));
        }),
    ) as unknown as typeof fetch;
    let settled = false;
    const pending = failure(apiFetch('/x')).finally(() => {
      settled = true;
    });
    await jest.advanceTimersByTimeAsync(14_999);
    expect(settled).toBe(false);
    await jest.advanceTimersByTimeAsync(1);
    expect(await pending).toMatchObject({
      code: 'CLIENT_NETWORK',
      status: 0,
      message: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
    });
  } finally {
    jest.useRealTimers();
  }
});
