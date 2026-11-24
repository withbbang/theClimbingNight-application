import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useSubmit } from '@/hooks/use-submit';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { meetupApi } from '@/lib/meetup-api';
import type { MeetupDetailResponse, MyApplicationResponse } from '@/types/api/meetup';

function useApplicationMutation(
  id: number,
  mutationFn: (reason: string) => Promise<MyApplicationResponse>,
) {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn,
    onSuccess: (my) => {
      notifySuccess();
      client.setQueryData<MeetupDetailResponse>(['meetups', 'detail', id], (old) =>
        old ? { ...old, my } : old,
      );
    },
    onError: notifyError,
    // 낡은 화면에서 눌렀거나 응답만 잃었을 수 있으니 실패해도 다시 받는다.
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['meetups'] });
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

export function useApply(id: number) {
  return useApplicationMutation(id, () => meetupApi.apply(id));
}

export function useCancelApplication(id: number) {
  return useApplicationMutation(id, (reason) => meetupApi.cancel(id, reason.trim() || undefined));
}
