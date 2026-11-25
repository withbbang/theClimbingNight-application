import { useState, type ReactNode } from 'react';

import type { UseQueryResult } from '@tanstack/react-query';

import { Notice } from '@/components/ui/Notice';
import { QueryState } from '@/components/ui/QueryState';
import { errorMessage } from '@/lib/error-message';

interface FreshQueryProps<T> {
  /** `refetchOnMount: 'always'`인 조회. */
  query: UseQueryResult<T>;
  children: (data: T) => ReactNode;
}

/**
 * 고치는 폼용. 화면을 연 뒤 새로 받은 값을 한 번만 넘긴다.
 * 낡은 값으로 전체를 덮어쓰지 않고, 뒤의 재조회가 입력을 지우지 않는다.
 */
export function FreshQuery<T>({ query, children }: FreshQueryProps<T>) {
  const [fresh, setFresh] = useState<{ data: T } | null>(null);
  if (fresh) {
    return children(fresh.data);
  }
  if (query.isFetchedAfterMount && query.isSuccess && !query.isFetching) {
    setFresh({ data: query.data });
    return null;
  }
  if (query.isError && !query.isFetching) {
    return (
      <Notice
        title="불러오지 못했어요"
        description={errorMessage(query.error)}
        actionLabel="다시 시도"
        onAction={() => void query.refetch()}
      />
    );
  }
  return (
    <QueryState<T> query={{ ...query, data: undefined, isError: false } as UseQueryResult<T>}>
      {() => null}
    </QueryState>
  );
}
