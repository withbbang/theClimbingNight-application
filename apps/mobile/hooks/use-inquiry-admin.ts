import { useQuery } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { inquiryApi } from '@/lib/inquiry-api';
import type { InquiryResponse } from '@/types/api/inquiry';

export function useAdminInquiries(status: InquiryResponse['status']) {
  return usePagedQuery(
    ['inquiries', 'admin', status],
    (page, size) => inquiryApi.all(status, page, size),
    useIsAdmin(),
  );
}

/** 답변을 고치는 폼용이라 열 때마다 새로 받는다. */
export function useAdminInquiry(id: number | null) {
  const admin = useIsAdmin();
  return useQuery({
    queryKey: ['inquiries', 'admin', 'detail', id],
    queryFn: () => inquiryApi.get(id ?? 0),
    enabled: admin && id !== null,
    refetchOnMount: 'always',
  });
}

export function useAnswerInquiry(id: number) {
  return useAdminMutation((answer: string) => inquiryApi.answer(id, answer), [['inquiries']]);
}
