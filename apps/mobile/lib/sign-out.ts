import { authApi } from '@/lib/auth-api';
import {
  clearRegisteredPushToken,
  getRegisteredPushToken,
  waitForPushRegistration,
} from '@/lib/push';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { signOutSocial } from '@/lib/social';

const MAX_WAIT_MS = 3000;

let deviceCleanUp: Promise<unknown> = Promise.resolve();

async function takePushToken() {
  const token = await getRegisteredPushToken().catch(() => null);
  await clearRegisteredPushToken().catch(() => {});
  return token ?? undefined;
}

/** 끝난 세션의 푸시 등록과 소셜 로그인을 정리한다. 실패해도 된다. */
export async function cleanUpSession(refreshToken: string) {
  const pushToken = takePushToken();
  deviceCleanUp = Promise.allSettled([pushToken, signOutSocial()]);
  const expoPushToken = await pushToken;
  // 늦게 끝난 등록이 로그아웃 뒤에 토큰을 되살리지 않게 한다.
  await waitForPushRegistration();
  await authApi.logout(refreshToken, expoPushToken).catch(() => {});
  await deviceCleanUp;
}

/** 새 로그인이 앞선 로그아웃의 기기 정리와 겹치지 않게 기다린다. 응답 없는 SDK에는 붙잡히지 않는다. */
export function waitForCleanUp(): Promise<unknown> {
  let timer: ReturnType<typeof setTimeout>;
  const limit = new Promise<void>((resolve) => {
    timer = setTimeout(resolve, MAX_WAIT_MS);
  });
  return Promise.race([deviceCleanUp, limit]).finally(() => clearTimeout(timer));
}

/** 이 기기에서 먼저 로그아웃한다. 서버와 소셜 로그아웃은 실패해도 된다. */
export async function signOut() {
  const session = sessionStore.getState();
  const cleared = sessionStore.signOut();
  queryClient.clear();
  if (session.status === 'authenticated') {
    await cleanUpSession(session.tokens.refreshToken);
  }
  await cleared;
}
