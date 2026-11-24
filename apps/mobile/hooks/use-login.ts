import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { useFocusEffect, useRouter } from 'expo-router';

import { authApi } from '@/lib/auth-api';
import { errorMessage } from '@/lib/error-message';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { loginNotice } from '@/lib/login-notice';
import { pendingSignup } from '@/lib/pending-signup';
import { sessionStore } from '@/lib/session-store';
import type { SocialAdapter } from '@/lib/social/types';
import type { SocialProvider } from '@/types/api/auth';

const RETURN_GRACE_MS = 4000;

/** 다른 앱에 다녀온 뒤에도 답이 없으면 부른다. 시스템 창이 잠깐 덮은 것(inactive)은 세지 않는다. */
function watchReturn(onSilent: () => void) {
  let left = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const subscription = AppState.addEventListener('change', (state) => {
    clearTimeout(timer);
    if (state === 'background') {
      left = true;
    } else if (state === 'active' && left) {
      timer = setTimeout(onSilent, RETURN_GRACE_MS);
    }
  });
  return () => {
    clearTimeout(timer);
    subscription.remove();
  };
}

export function useLogin() {
  const router = useRouter();
  const busy = useRef(false);
  // 새로 시도하거나 화면을 떠나면 올라간다. 예전 시도의 늦은 결과를 버린다.
  const attempt = useRef(0);
  const [pending, setPending] = useState<SocialProvider | null>(null);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      const notice = loginNotice.take();
      if (notice) {
        setError(notice);
      }
    }, []),
  );

  useEffect(
    () => () => {
      attempt.current += 1;
    },
    [],
  );

  function unlock() {
    busy.current = false;
    setPending(null);
  }

  async function login(adapter: SocialAdapter) {
    if (busy.current) {
      return;
    }
    busy.current = true;
    const current = (attempt.current += 1);
    const isCurrent = () => attempt.current === current;
    setPending(adapter.provider);
    setError(null);
    pendingSignup.clear();
    try {
      // 잠금을 풀어도 다른 시도가 없으면 늦게 온 답을 받는다.
      const stopWatching = watchReturn(() => {
        if (isCurrent()) {
          unlock();
        }
      });
      const social = await adapter.signIn().finally(stopWatching);
      if (social.type === 'cancelled' || !isCurrent()) {
        return;
      }
      busy.current = true;
      setPending(adapter.provider);
      const result = await authApi.login(adapter.provider, social.token);
      if (!isCurrent()) {
        return;
      }
      if (result.status === 'SIGNUP_REQUIRED') {
        pendingSignup.set(result.signupToken);
        router.push('/signup');
        return;
      }
      notifySuccess();
      await sessionStore.signIn(result.tokens);
      router.dismissTo('/');
    } catch (e) {
      if (!isCurrent()) {
        return;
      }
      notifyError();
      setError(errorMessage(e));
    } finally {
      if (isCurrent()) {
        unlock();
      }
    }
  }

  return { login, pending, error };
}
