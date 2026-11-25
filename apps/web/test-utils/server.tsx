import type { ReactElement } from 'react';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';

export interface Reply {
  status: number;
  body?: unknown;
}

export const ok = (data: unknown): Reply => ({ status: 200, body: { success: true, data } });

export const fail = (status: number, code: string, message: string): Reply => ({
  status,
  body: { success: false, error: { code, message } },
});

/** 경로별 응답을 정한다. 없는 경로는 네트워크 오류다. */
export function mockServer(routes: Record<string, Reply | (() => Reply)>) {
  const calls: string[] = [];
  globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input).replace('/api/v1', '');
    calls.push(path);
    const route = routes[path];
    if (!route) {
      throw new TypeError('Failed to fetch');
    }
    const reply = typeof route === 'function' ? route() : route;
    return new Response(reply.body ? JSON.stringify(reply.body) : null, { status: reply.status });
  });
  return { calls };
}

export function renderWithQuery(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return { client, ...render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>) };
}
