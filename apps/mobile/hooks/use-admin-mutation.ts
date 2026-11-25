import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';

import { useSubmit } from '@/hooks/use-submit';
import { notifyError, notifySuccess } from '@/lib/haptics';

/** 운영진의 쓰기. 다른 운영진이 먼저 처리했을 수 있으니 실패해도 다시 받는다. */
export function useAdminMutation<TData, TVariables = void>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  keys: QueryKey[],
  on: {
    success?: (data: TData, variables: TVariables) => void;
    /** 보낸 줄이 목록에서 사라져도 불린다. */
    error?: (error: Error) => void;
  } = {},
) {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn,
    onSuccess: (data, variables) => {
      notifySuccess();
      on.success?.(data, variables);
    },
    onError: (error) => {
      notifyError();
      on.error?.(error);
    },
    onSettled: () => {
      for (const queryKey of keys) {
        void client.invalidateQueries({ queryKey });
      }
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
