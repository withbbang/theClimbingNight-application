import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { memberApi } from '@/lib/member-api';

export function useMembers(q: string) {
  return usePagedQuery(
    ['members', q],
    (page, size) => memberApi.list(q, page, size),
    useIsAdmin(),
    (member) => member.userId,
  );
}

export function useRevokeMember() {
  return useAdminMutation(
    // 204라 값이 없으므로 성공을 true로 알린다.
    (userId: number) => memberApi.revoke(userId).then(() => true),
    [['members'], ['meetups']],
  );
}
