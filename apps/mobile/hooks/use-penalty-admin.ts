import { useQuery } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { penaltyApi } from '@/lib/penalty-api';

export function usePenaltySummaries() {
  return usePagedQuery(
    ['penalties', 'admin', 'list'],
    penaltyApi.summaries,
    useIsAdmin(),
    (summary) => summary.rosterMemberId,
  );
}

export function useRosterPenalties(rosterMemberId: number | null) {
  const admin = useIsAdmin();
  return useQuery({
    queryKey: ['penalties', 'admin', rosterMemberId],
    queryFn: () => penaltyApi.ofRoster(rosterMemberId ?? 0),
    enabled: admin && rosterMemberId !== null,
  });
}

export function useRevokePenalty() {
  return useAdminMutation(
    ({ id, reason }: { id: number; reason: string }) => penaltyApi.revoke(id, reason),
    [['meetups'], ['penalties']],
  );
}
