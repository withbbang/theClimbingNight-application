import { useRef } from 'react';

import type { UseMutationResult } from '@tanstack/react-query';

/** 다시 그려지기 전에 연달아 눌러도 한 번만 보낸다. */
export function useSubmit<TData, TVariables>(
  mutation: UseMutationResult<TData, Error, TVariables>,
) {
  const busy = useRef(false);
  return async (variables: TVariables): Promise<TData | undefined> => {
    if (busy.current) {
      return undefined;
    }
    busy.current = true;
    try {
      return await mutation.mutateAsync(variables);
    } catch {
      // 오류는 mutation.error로 화면에 보여 준다.
      return undefined;
    } finally {
      busy.current = false;
    }
  };
}
