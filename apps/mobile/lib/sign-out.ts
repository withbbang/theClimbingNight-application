import { authApi } from '@/lib/auth-api';
import { clearRegisteredPushToken, getRegisteredPushToken } from '@/lib/push';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { signOutSocial } from '@/lib/social';

async function takePushToken() {
  const token = await getRegisteredPushToken().catch(() => null);
  await clearRegisteredPushToken().catch(() => {});
  return token ?? undefined;
}

/** 이 기기에서 먼저 로그아웃한다. 서버와 소셜 로그아웃은 실패해도 된다. */
export async function signOut() {
  const session = sessionStore.getState();
  const cleared = sessionStore.signOut();
  queryClient.clear();
  await cleared;
  if (session.status !== 'authenticated') {
    return;
  }
  const expoPushToken = await takePushToken();
  await Promise.allSettled([
    authApi.logout(session.tokens.refreshToken, expoPushToken),
    signOutSocial(),
  ]);
}
