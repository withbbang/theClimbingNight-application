import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { useDropFromPages, usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { memberApi } from '@/lib/member-api';
import type { MemberResponse } from '@/types/api/member';

const idOf = (member: MemberResponse) => member.userId;

export function useMembers(q: string) {
  return usePagedQuery(
    ['members', q],
    (page, size) => memberApi.list(q, page, size),
    useIsAdmin(),
    idOf,
  );
}

export function useRevokeMember() {
  const drop = useDropFromPages(['members'], idOf);
  return useAdminMutation(
    // 204라 값이 없으므로 성공을 true로 알린다.
    (userId: number) => memberApi.revoke(userId).then(() => true),
    [['members'], ['meetups']],
    { success: (_, userId) => drop(userId) },
  );
}
