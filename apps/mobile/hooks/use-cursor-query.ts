import { useInfiniteQuery, type QueryKey } from '@tanstack/react-query';

import type { CursorResponse } from '@/types/api/common';

/** usePagedQuery의 커서 판. 끝까지 받으면 nextCursor가 null이다. */
export function useCursorQuery<T>(
  queryKey: QueryKey,
  fetchPage: (cursor: string | null) => Promise<CursorResponse<T>>,
  enabled: boolean,
  idOf: (item: T) => number = (item) => (item as { id: number }).id,
) {
  return useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchPage(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: ({ nextCursor }) => nextCursor ?? undefined,
    // 받는 사이 새 글이 올라오거나 숨겨지면 같은 글이 두 페이지에 걸칠 수 있다.
    select: ({ pages }) => [
      ...new Map(pages.flatMap((p) => p.items).map((item) => [idOf(item), item])).values(),
    ],
    enabled,
  });
}
