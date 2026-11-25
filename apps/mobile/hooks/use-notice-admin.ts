import { useQuery, useQueryClient } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin } from '@/hooks/use-role';
import { noticeApi } from '@/lib/notice-api';
import type { NoticeRequest } from '@/types/api/notice';

const LIST_KEY = ['notices', 'admin', 'list'];
const detailKey = (id: number | null) => ['notices', 'detail', id];

export function useAdminNotices() {
  return usePagedQuery(LIST_KEY, noticeApi.list, useIsAdmin());
}

/** 고치는 폼용이라 열 때마다 새로 받는다. */
export function useNotice(id: number | null) {
  const admin = useIsAdmin();
  return useQuery({
    queryKey: detailKey(id),
    queryFn: () => noticeApi.get(id ?? 0),
    enabled: admin && id !== null,
    refetchOnMount: 'always',
  });
}

/** id가 null이면 새 공지. */
export function useSaveNotice(id: number | null) {
  return useAdminMutation(
    (body: NoticeRequest) => (id === null ? noticeApi.create(body) : noticeApi.update(id, body)),
    [['notices']],
  );
}

export function useDeleteNotice(id: number) {
  const client = useQueryClient();
  return useAdminMutation(
    // 204라 값이 없으므로 성공을 true로 알린다.
    () => noticeApi.remove(id).then(() => true),
    // 아직 열려 있는 상세는 다시 받지 않는다. (없는 공지라 안내 화면이 깜빡인다)
    [LIST_KEY, ['notices', 'recent']],
    { success: () => client.removeQueries({ queryKey: detailKey(id) }) },
  );
}
