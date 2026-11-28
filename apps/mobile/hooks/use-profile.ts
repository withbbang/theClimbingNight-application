import { Platform } from 'react-native';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { profileKey } from '@/hooks/use-user-profile';
import { useSubmit } from '@/hooks/use-submit';
import { ApiError } from '@/lib/api-error';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { sessionStore } from '@/lib/session-store';
import { signOut } from '@/lib/sign-out';
import { appleAuthorizationCode } from '@/lib/social/apple';
import { userApi } from '@/lib/user-api';

const ME = ['users', 'me'];

export function useChangeNickname() {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: userApi.changeNickname,
    onSuccess: async (user) => {
      notifySuccess();
      // 먼저 나간 조회가 옛 닉네임으로 되돌리지 않게 한다.
      await client.cancelQueries({ queryKey: ME });
      client.setQueryData(ME, user);
      await sessionStore.updateUser(user);
      // 스택에 열린 내 프로필이 옛 닉네임을 보이지 않게 한다.
      await client.invalidateQueries({ queryKey: profileKey(user.id) });
    },
    onError: notifyError,
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

export function useWithdraw() {
  const mutation = useMutation({
    // false면 사용자가 Apple 재인증 창을 닫은 것이다. 탈퇴하지 않고 화면에 남는다.
    mutationFn: async (): Promise<boolean> => {
      try {
        await userApi.withdraw();
      } catch (error) {
        if (!(isAppleReauthRequired(error) && Platform.OS === 'ios')) {
          throw error;
        }
        const code = await appleAuthorizationCode();
        if (code === null) {
          return false;
        }
        await userApi.withdraw(code);
      }
      return true;
    },
    // 서버 로그아웃 응답을 기다리지 않는다. 기기에서는 바로 로그아웃된다.
    onSuccess: (withdrawn) => {
      if (withdrawn) {
        void signOut();
      }
    },
    onError: notifyError,
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

function isAppleReauthRequired(error: unknown) {
  return error instanceof ApiError && error.code === 'AUTH_APPLE_REAUTH_REQUIRED';
}
