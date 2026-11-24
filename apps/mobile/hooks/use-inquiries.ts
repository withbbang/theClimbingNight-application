import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useRole } from '@/hooks/use-role';
import { useSubmit } from '@/hooks/use-submit';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { inquiryApi } from '@/lib/inquiry-api';

const PAGE_SIZE = 20;

export function useMyInquiries() {
  return useInfiniteQuery({
    queryKey: ['inquiries', 'me', 'list'],
    queryFn: ({ pageParam }) => inquiryApi.mine(pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: ({ page, totalPages }) => (page + 1 < totalPages ? page + 1 : undefined),
    // 불러오는 사이 새 문의가 생기면 페이지가 밀려 같은 항목이 다시 올 수 있다.
    select: ({ pages }) => [
      ...new Map(pages.flatMap((p) => p.items).map((item) => [item.id, item])).values(),
    ],
    enabled: useRole() !== null,
  });
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
