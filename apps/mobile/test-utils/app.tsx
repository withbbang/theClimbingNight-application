import { act, renderRouter } from 'expo-router/testing-library';

export interface Reply {
  status: number;
  body?: unknown;
}

export const ok = (data: unknown): Reply => ({ status: 200, body: { success: true, data } });

export const fail = (status: number, code: string, message: string): Reply => ({
  status,
  body: { success: false, error: { code, message } },
});

/** 경로별 응답을 정한다. 'POST /x'처럼 메서드를 붙일 수 있다. 값이 함수면 부를 때마다 실행하고, 없는 경로는 네트워크 오류다. */
export function mockServer(routes: Record<string, Reply | (() => Reply | Promise<Reply>)>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  globalThis.fetch = jest.fn(async (url: string, init: RequestInit) => {
    const path = url.replace('https://api.test/api/v1', '');
    const method = init.method ?? 'GET';
    calls.push({ method, path, body: init.body ? JSON.parse(init.body as string) : undefined });
    const route = routes[`${method} ${path}`] ?? routes[path];
    if (!route) {
      throw new TypeError('Network request failed');
    }
    const reply = typeof route === 'function' ? await route() : route;
    return new Response(reply.body ? JSON.stringify(reply.body) : null, { status: reply.status });
  }) as unknown as typeof fetch;
  return {
    calls,
    count: (path: string) =>
      calls.filter((c) => c.path === path || `${c.method} ${c.path}` === path).length,
  };
}

export async function flush() {
  await act(async () => {
    // renderRouter가 가짜 타이머를 켠다.
    await jest.advanceTimersByTimeAsync(20);
  });
}

export async function renderApp(initialUrl: string) {
  const app = await renderRouter('./app', { initialUrl });
  await flush();
  return app;
}
