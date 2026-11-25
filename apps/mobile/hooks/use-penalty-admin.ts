import { useQuery, useQueryClient } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { penaltyApi } from '@/lib/penalty-api';
import type { RosterPenaltiesResponse } from '@/types/api/penalty';

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

export function useRevokePenalty(rosterMemberId: number) {
  const client = useQueryClient();
  return useAdminMutation(
    ({ id, reason }: { id: number; reason: string }) => penaltyApi.revoke(id, reason),
    [['meetups'], ['penalties']],
    {
      // 다시 받는 동안 끝난 동작을 또 누르지 않게 한다.
      success: (_, { id, reason }) =>
        client.setQueryData<RosterPenaltiesResponse>(
          ['penalties', 'admin', rosterMemberId],
          (old) =>
            old && {
              ...old,
              penalties: old.penalties.map((penalty) =>
                penalty.id === id ? { ...penalty, active: false, revokeReason: reason } : penalty,
              ),
            },
        ),
    },
  );
}
