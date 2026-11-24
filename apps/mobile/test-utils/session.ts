import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';
import type { Role } from '@/types/api/user';

/** 테스트마다 정해진 시각과 역할로 시작한다. null이면 비로그인. */
export async function startAs(role: Role | null, now = '2026-11-24T03:00:00Z') {
  jest.useFakeTimers({ now: new Date(now) });
  queryClient.clear();
  queryClient.setDefaultOptions({ queries: { retry: false, staleTime: 30_000 } });
  await sessionStore.signOut();
  if (role) {
    await sessionStore.signIn(tokens('1', role));
  }
}
