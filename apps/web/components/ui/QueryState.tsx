'use client';

import type { ReactNode } from 'react';

import type { UseQueryResult } from '@tanstack/react-query';

import { Notice } from '@/components/ui/Notice';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { buttonClass } from '@/components/ui/styles';
import { errorMessage } from '@/lib/error-message';

interface QueryStateProps<T> {
  query: Pick<UseQueryResult<T>, 'data' | 'isError' | 'error' | 'refetch'>;
  children: (data: T) => ReactNode;
}

/** 첫 로딩과 첫 실패만 대신 보여 준다. 받은 데이터가 있으면 다시 받기에 실패해도 그대로 둔다. */
export function QueryState<T>({ query, children }: QueryStateProps<T>) {
  if (query.data !== undefined) {
    return children(query.data);
  }
  if (query.isError) {
    return (
      <Notice
        title="불러오지 못했어요"
        description={errorMessage(query.error)}
        action={
          <button type="button" className={buttonClass} onClick={() => void query.refetch()}>
            다시 시도
          </button>
        }
      />
    );
  }
  return <PulseHolds />;
}
