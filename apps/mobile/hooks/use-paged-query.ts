import {
  useInfiniteQuery,
  useQueryClient,
  type InfiniteData,
  type QueryKey,
} from '@tanstack/react-query';

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

/** 처리한 항목을 목록에서 바로 뺀다. 다시 받지 못해도 또 누르지 않게 한다. */
export function useDropFromPages<T>(queryKey: QueryKey, idOf: (item: T) => number) {
  const client = useQueryClient();
  return (id: number) =>
    client.setQueriesData<InfiniteData<PageResponse<T>>>(
      { queryKey },
      (old) =>
        old && {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            items: page.items.filter((item) => idOf(item) !== id),
          })),
        },
    );
}
