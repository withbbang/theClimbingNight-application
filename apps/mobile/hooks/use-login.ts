import { useCallback, useRef, useState } from 'react';

import { useFocusEffect, useRouter } from 'expo-router';

import { authApi } from '@/lib/auth-api';
import { errorMessage } from '@/lib/error-message';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { loginNotice } from '@/lib/login-notice';
import { pendingSignup } from '@/lib/pending-signup';
import { sessionStore } from '@/lib/session-store';
import type { SocialAdapter } from '@/lib/social/types';
import type { SocialProvider } from '@/types/api/auth';

export function useLogin() {
  const router = useRouter();
  const busy = useRef(false);
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

  async function login(adapter: SocialAdapter) {
    if (busy.current) {
      return;
    }
    busy.current = true;
    setPending(adapter.provider);
    setError(null);
    pendingSignup.clear();
    try {
      const social = await adapter.signIn();
      if (social.type === 'cancelled') {
        return;
      }
      const result = await authApi.login(adapter.provider, social.token);
      if (result.status === 'SIGNUP_REQUIRED') {
        pendingSignup.set(result.signupToken);
        router.push('/signup');
        return;
      }
      notifySuccess();
      await sessionStore.signIn(result.tokens);
      router.dismissTo('/');
    } catch (e) {
      notifyError();
      setError(errorMessage(e));
    } finally {
      busy.current = false;
      setPending(null);
    }
  }

  return { login, pending, error };
}
