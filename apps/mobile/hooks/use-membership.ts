import { useEffect } from 'react';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useRole } from '@/hooks/use-role';
import { useSubmit } from '@/hooks/use-submit';
import { ApiError } from '@/lib/api-error';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { membershipApi } from '@/lib/membership-api';

const KEY = ['membership-requests', 'me'];

export function useMyMembershipRequest() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: KEY,
    queryFn: membershipApi.getMine,
    enabled: useRole() === 'USER',
  });
  const approved = query.data?.status === 'APPROVED';

  // 승인됐는데 아직 USER면 방금 승인됐거나 자격이 해제된 것이다. 내 정보로 가린다.
  useEffect(() => {
    if (approved) {
      void client.invalidateQueries({ queryKey: ['users', 'me'] });
    }
  }, [approved, client, query.dataUpdatedAt]);

  return query;
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
