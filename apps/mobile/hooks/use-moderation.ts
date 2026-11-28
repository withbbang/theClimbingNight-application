import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { useDropFromPages, usePagedQuery } from '@/hooks/use-paged-query';
import { useIsAdmin, useRole } from '@/hooks/use-role';
import { useSubmit } from '@/hooks/use-submit';
import { ApiError } from '@/lib/api-error';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { moderationApi } from '@/lib/moderation-api';
import type {
  ReportCreateRequest,
  ReportTargetAction,
  ReportTargetResponse,
  ReportTargetType,
} from '@/types/api/moderation';

const BLOCKS = ['blocks'];
const REPORT_TARGETS = ['report-targets'];
const TYPE_ORDER: Record<ReportTargetType, number> = { POST: 0, COMMENT: 1, USER: 2 };

/** 게시물과 사용자의 ID가 겹칠 수 있어 종류까지 넣은 목록 키. */
export const reportTargetKey = (target: Pick<ReportTargetResponse, 'targetType' | 'targetId'>) =>
  target.targetId * 3 + TYPE_ORDER[target.targetType];

/**
 * 이미 신고했거나(409) 글이 이미 내려갔으면(404) 신고한 사람에게는 접수와 같다.
 * 내려간 글을 404로 알리는 건 존재 여부를 흘리지 않으려는 API 규칙이다.
 */
function alreadyHandled(error: unknown) {
  return (
    error instanceof ApiError &&
    ((error.status === 409 && error.code === 'REPORT_ALREADY_SUBMITTED') ||
      (error.status === 404 && error.code === 'COMMON_NOT_FOUND'))
  );
}

export function useReport() {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: ReportCreateRequest) =>
      moderationApi.report(request).then(
        () => true,
        (error: unknown) => {
          if (alreadyHandled(error)) {
            return true;
          }
          throw error;
        },
      ),
    onSuccess: notifySuccess,
    onError: notifyError,
    // 신고가 쌓여 자동으로 숨겨졌을 수 있다.
    onSettled: () => void client.invalidateQueries({ queryKey: ['posts'] }),
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

export function useBlocks() {
  const role = useRole();
  return useQuery({ queryKey: BLOCKS, queryFn: moderationApi.blocks, enabled: role !== null });
}

/** 차단하거나 풀면 서로의 글이 보이거나 사라지므로 피드를 다시 받는다. */
function useBlockMutation(send: (userId: number) => Promise<unknown>) {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: (userId: number) => send(userId).then(() => true),
    onSuccess: notifySuccess,
    onError: notifyError,
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['posts'] });
      void client.invalidateQueries({ queryKey: BLOCKS });
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

export function useBlockUser() {
  return useBlockMutation(moderationApi.block);
}

export function useUnblockUser() {
  return useBlockMutation(moderationApi.unblock);
}

export function useReportTargets() {
  return usePagedQuery(REPORT_TARGETS, moderationApi.targets, useIsAdmin(), reportTargetKey);
}

interface TargetAction {
  targetType: ReportTargetType;
  targetId: number;
  action: ReportTargetAction;
}

/** 조치하면 그 대상의 열린 신고가 모두 닫히므로 목록에서 뺀다. */
export function useReportTargetAction() {
  const drop = useDropFromPages<ReportTargetResponse>(REPORT_TARGETS, reportTargetKey);
  return useAdminMutation(
    async ({ targetType, targetId, action }: TargetAction) => {
      await moderationApi.act(targetType, targetId, action);
      return true;
    },
    [REPORT_TARGETS, ['posts']],
    { success: (_, target) => drop(reportTargetKey(target)) },
  );
}

/** 운영진이 신고 없이 글을 바로 숨긴다. */
export function useHidePost() {
  return useAdminMutation(
    (postId: number) => moderationApi.act('POST', postId, 'HIDE').then(() => true),
    [REPORT_TARGETS, ['posts']],
  );
}
