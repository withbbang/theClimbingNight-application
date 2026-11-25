import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { noticeApi } from '@/lib/notice-api';

const PAGE_SIZE = 20;

export function useNotices() {
  return useInfiniteQuery({
    queryKey: ['notices'],
    queryFn: ({ pageParam }) => noticeApi.list(pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: ({ page, totalPages }) => (page + 1 < totalPages ? page + 1 : undefined),
    // 새 공지가 올라와 쪽이 밀리면 같은 공지가 두 쪽에 걸친다.
    select: ({ pages }) => [
      ...new Map(pages.flatMap((page) => page.items).map((item) => [item.id, item])).values(),
    ],
  });
}

export function useNotice(id: number | null) {
  return useQuery({
    queryKey: ['notices', id],
    queryFn: () => noticeApi.get(id ?? 0),
    enabled: id !== null,
  });
}
