import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useRole } from '@/hooks/use-role';
import { useSubmit } from '@/hooks/use-submit';
import { notifyError } from '@/lib/haptics';
import { notificationSettingApi } from '@/lib/notification-setting-api';

const KEY = ['notification-settings', 'me'];

export function useNotificationSettings() {
  return useQuery({
    queryKey: KEY,
    queryFn: notificationSettingApi.getMine,
    enabled: useRole() !== null,
  });
}

/** 저장하는 동안의 값은 mutation.variables에 있다. 실패하면 서버 값으로 돌아간다. */
export function useUpdateNotificationSettings() {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: notificationSettingApi.update,
    onMutate: () => client.cancelQueries({ queryKey: KEY }),
    onSuccess: (settings) => client.setQueryData(KEY, settings),
    onError: () => {
      notifyError();
      void client.invalidateQueries({ queryKey: KEY });
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
