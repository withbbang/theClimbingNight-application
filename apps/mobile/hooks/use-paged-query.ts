import { useInfiniteQuery, type QueryKey } from '@tanstack/react-query';

import type { PageResponse } from '@/types/api/common';

const PAGE_SIZE = 20;

export function usePagedQuery<T>(
  queryKey: QueryKey,
  fetchPage: (page: number, size: number) => Promise<PageResponse<T>>,
  enabled: boolean,
  idOf: (item: T) => number = (item) => (item as { id: number }).id,
) {
  return useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchPage(pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: ({ page, totalPages }) => (page + 1 < totalPages ? page + 1 : undefined),
    // 불러오는 사이 항목이 늘면 페이지가 밀려 같은 항목이 다시 올 수 있다.
    select: ({ pages }) => [
      ...new Map(pages.flatMap((p) => p.items).map((item) => [idOf(item), item])).values(),
    ],
    enabled,
  });
}
