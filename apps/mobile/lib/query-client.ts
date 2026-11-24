import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';

import { ApiError } from '@/lib/api-error';

const MAX_RETRIES = 2;

/** 등급이 바뀌어 막혔을 수 있으니 내 정보를 다시 받아 화면에 반영한다. */
function syncRole(error: Error) {
  if (error instanceof ApiError && error.status === 403 && error.code === 'AUTH_FORBIDDEN') {
    void queryClient.invalidateQueries({ queryKey: ['users', 'me'] });
  }
}

export const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: syncRole }),
  mutationCache: new MutationCache({ onError: syncRole }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < MAX_RETRIES;
      },
    },
  },
});
