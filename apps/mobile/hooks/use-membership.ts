import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useRole } from '@/hooks/use-role';
import { useSubmit } from '@/hooks/use-submit';
import { ApiError } from '@/lib/api-error';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { membershipApi } from '@/lib/membership-api';

const KEY = ['membership-requests', 'me'];

export function useMyMembershipRequest() {
  return useQuery({
    queryKey: KEY,
    queryFn: membershipApi.getMine,
    enabled: useRole() === 'USER',
  });
}

export function useApplyMembership() {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: membershipApi.apply,
    onSuccess: (request) => {
      notifySuccess();
      client.setQueryData(KEY, request);
    },
    onError: (error) => {
      notifyError();
      if (error instanceof ApiError && error.code === 'MEMBERSHIP_ALREADY_MEMBER') {
        void client.invalidateQueries({ queryKey: ['users', 'me'] });
      }
    },
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['membership-requests'] });
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
