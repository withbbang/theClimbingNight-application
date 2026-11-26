import { useQuery } from '@tanstack/react-query';

import { fetchPost } from '@/lib/post-api';

export function usePost(id: number | null) {
  return useQuery({
    queryKey: ['posts', id],
    queryFn: () => fetchPost(id ?? 0),
    enabled: id !== null,
  });
}
