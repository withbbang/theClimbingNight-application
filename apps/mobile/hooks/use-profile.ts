import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useSubmit } from '@/hooks/use-submit';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { sessionStore } from '@/lib/session-store';
import { signOut } from '@/lib/sign-out';
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
    },
    onError: notifyError,
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

export function useWithdraw() {
  const mutation = useMutation({
    mutationFn: async () => {
      await userApi.withdraw();
      return true;
    },
    // 서버 로그아웃 응답을 기다리지 않는다. 기기에서는 바로 로그아웃된다.
    onSuccess: () => {
      void signOut();
    },
    onError: notifyError,
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
