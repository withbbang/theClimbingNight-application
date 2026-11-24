import { useQuery } from '@tanstack/react-query';

import { useIsMember } from '@/hooks/use-role';
import { penaltyApi } from '@/lib/penalty-api';

export function useMyPenalties() {
  return useQuery({
    queryKey: ['penalties', 'me'],
    queryFn: penaltyApi.mine,
    enabled: useIsMember(),
  });
}
