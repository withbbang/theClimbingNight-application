import { useQuery } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { useDropFromPages, usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { membershipApi } from '@/lib/membership-api';
import { rosterApi } from '@/lib/roster-api';
import type {
  MembershipApproveRequest,
  MembershipReviewItemResponse,
} from '@/types/api/membership';

const KEYS = [['membership-requests'], ['members'], ['roster-members']];
const PENDING_KEY = ['membership-requests', 'admin', 'PENDING'];
const SEARCH_SIZE = 20;

export function usePendingRequests() {
  return usePagedQuery(PENDING_KEY, membershipApi.pending, useIsAdmin());
}

export function useRosterSearch(q: string) {
  const admin = useIsAdmin();
  return useQuery({
    queryKey: ['roster-members', 'unlinked', q],
    queryFn: () => rosterApi.search(q, 0, SEARCH_SIZE),
    enabled: admin && q !== '',
  });
}

function useDropRequest() {
  return useDropFromPages(PENDING_KEY, (request: MembershipReviewItemResponse) => request.id);
}

export function useApproveRequest() {
  const drop = useDropRequest();
  return useAdminMutation(
    ({ id, body }: { id: number; body: MembershipApproveRequest }) =>
      membershipApi.approve(id, body),
    KEYS,
    { success: (_, { id }) => drop(id) },
  );
}

export function useRejectRequest() {
  const drop = useDropRequest();
  return useAdminMutation(
    ({ id, reason }: { id: number; reason: string }) => membershipApi.reject(id, reason),
    KEYS,
    { success: (_, { id }) => drop(id) },
  );
}

export function useBulkApprove() {
  return useAdminMutation(() => membershipApi.bulkApprove(), KEYS);
}
