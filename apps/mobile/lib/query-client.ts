import { MutationCache, QueryCache, QueryClient, type Query } from '@tanstack/react-query';

import { ApiError } from '@/lib/api-error';

const MAX_RETRIES = 2;
/** 로그인과 상관없이 누구에게나 같은 조회. */
const PUBLIC_KEYS: unknown[] = ['notices', 'app-settings'];

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

/**
 * 세션이 바뀔 때 앞사람의 데이터를 지운다.
 * 공개 조회는 화면이 계속 보고 있으므로 지우지 않고 처음부터 다시 받는다. (지우면 화면이 지워진 쿼리에 붙어 남는다)
 */
export function clearSessionCache(): Promise<void> {
  const isPublic = ({ queryKey }: Query) => PUBLIC_KEYS.includes(queryKey[0]);
  queryClient.removeQueries({ predicate: (query) => !isPublic(query) });
  queryClient.getMutationCache().clear();
  return queryClient.resetQueries({ predicate: isPublic });
}
