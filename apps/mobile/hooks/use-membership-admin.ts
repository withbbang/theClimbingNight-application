import { useQuery } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { membershipApi } from '@/lib/membership-api';
import { rosterApi } from '@/lib/roster-api';
import type { MembershipApproveRequest } from '@/types/api/membership';

const KEYS = [['membership-requests'], ['members'], ['roster-members']];
const SEARCH_SIZE = 20;

export function usePendingRequests() {
  return usePagedQuery(
    ['membership-requests', 'admin', 'PENDING'],
    membershipApi.pending,
    useIsAdmin(),
  );
}

export function useRosterSearch(q: string) {
  const admin = useIsAdmin();
  return useQuery({
    queryKey: ['roster-members', 'unlinked', q],
    queryFn: () => rosterApi.search(q, 0, SEARCH_SIZE),
    enabled: admin && q !== '',
  });
}

export function useApproveRequest() {
  return useAdminMutation(
    ({ id, body }: { id: number; body: MembershipApproveRequest }) =>
      membershipApi.approve(id, body),
    KEYS,
  );
}

export function useRejectRequest() {
  return useAdminMutation(
    ({ id, reason }: { id: number; reason: string }) => membershipApi.reject(id, reason),
    KEYS,
  );
}

export function useBulkApprove() {
  return useAdminMutation(() => membershipApi.bulkApprove(), KEYS);
}
