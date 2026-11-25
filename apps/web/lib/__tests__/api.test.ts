import { apiFetch } from '@/lib/api';
import { isNotFound } from '@/lib/api-error';
import { fail, mockServer, ok } from '@/test-utils/server';

afterEach(() => {
  vi.useRealTimers();
});

it('calls the same origin', async () => {
  mockServer({ '/notices/1': ok({ id: 1 }) });

  expect(await apiFetch('/notices/1')).toEqual({ id: 1 });
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/v1/notices/1');
});

it('throws ApiError with the server message', async () => {
  mockServer({ '/notices/1': fail(404, 'COMMON_NOT_FOUND', '요청한 항목을 찾을 수 없습니다.') });

  const error = await apiFetch('/notices/1').catch((e: unknown) => e);

  expect(error).toMatchObject({
    code: 'COMMON_NOT_FOUND',
    message: '요청한 항목을 찾을 수 없습니다.',
  });
  expect(isNotFound(error)).toBe(true);
});

it.each([502, 200])('reports an unreadable %i as CLIENT_BAD_RESPONSE', async (status) => {
  globalThis.fetch = vi.fn(async () => new Response('<html>', { status }));

  await expect(apiFetch('/x')).rejects.toMatchObject({ code: 'CLIENT_BAD_RESPONSE', status });
});

it('reports a lost connection', async () => {
  mockServer({});

  await expect(apiFetch('/x')).rejects.toMatchObject({ code: 'CLIENT_NETWORK' });
});

it('reports a timeout after 15 seconds', async () => {
  vi.useFakeTimers();
  globalThis.fetch = vi.fn(
    (_url, init) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('', 'AbortError')));
      }),
  );
  const settled = vi.fn();
  const result = apiFetch('/x').catch((e: unknown) => {
    settled();
    return e;
  });

  await vi.advanceTimersByTimeAsync(14_999);
  expect(settled).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(1);
  expect(await result).toMatchObject({ code: 'CLIENT_TIMEOUT' });
});
