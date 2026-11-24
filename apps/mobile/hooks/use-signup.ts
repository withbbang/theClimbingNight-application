import { useEffect, useRef, useState } from 'react';

import { useRouter } from 'expo-router';

import { ApiError } from '@/lib/api-error';
import { authApi } from '@/lib/auth-api';
import { errorMessage } from '@/lib/error-message';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { loginNotice } from '@/lib/login-notice';
import { nicknameError } from '@/lib/nickname';
import { pendingSignup } from '@/lib/pending-signup';
import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';

const RESTART_CODES = ['AUTH_SIGNUP_EXPIRED', 'AUTH_ALREADY_REGISTERED'];

export function useSignup(signupToken: string | null) {
  const router = useRouter();
  const busy = useRef(false);
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const left = useRef(false);
  useEffect(() => {
    left.current = false;
    return () => {
      left.current = true;
    };
  }, []);

  function changeNickname(value: string) {
    setNickname(value);
    setError(null);
  }

  async function submit() {
    if (busy.current || signupToken === null) {
      return;
    }
    const invalid = nicknameError(nickname);
    if (invalid) {
      notifyError();
      setError(invalid);
      return;
    }
    busy.current = true;
    setSubmitting(true);
    setError(null);
    try {
      const tokens = await authApi.signup(signupToken, nickname.trim());
      // 화면을 떠났으면 적용하지 않는다. 계정은 만들어졌으니 다시 로그인하면 된다.
      if (left.current) {
        return;
      }
      pendingSignup.clear();
      notifySuccess();
      queryClient.clear();
      await sessionStore.signIn(tokens);
      if (!left.current) {
        router.dismissTo('/');
      }
    } catch (e) {
      if (left.current) {
        return;
      }
      notifyError();
      if (e instanceof ApiError && RESTART_CODES.includes(e.code)) {
        pendingSignup.clear();
        loginNotice.set(e.message);
        router.dismissTo('/login');
        return;
      }
      setError(errorMessage(e));
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  return { nickname, changeNickname, error, submitting, submit };
}
