import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { usePagedQuery } from '@/hooks/use-paged-query';
import { useRole } from '@/hooks/use-role';
import { useSubmit } from '@/hooks/use-submit';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { inquiryApi } from '@/lib/inquiry-api';

export function useMyInquiries() {
  const role = useRole();
  return usePagedQuery(['inquiries', 'me', 'list'], inquiryApi.mine, role !== null);
}

export function useMyInquiry(id: number | null) {
  const role = useRole();
  return useQuery({
    queryKey: ['inquiries', 'me', id],
    queryFn: () => inquiryApi.getMine(id ?? 0),
    enabled: role !== null && id !== null,
  });
}

export function useCreateInquiry() {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: inquiryApi.create,
    onSuccess: notifySuccess,
    onError: notifyError,
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['inquiries'] });
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
