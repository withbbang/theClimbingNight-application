import { useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { IdentificationCardIcon } from 'phosphor-react-native/src/icons/IdentificationCard';
import Animated, { useReducedMotion, ZoomIn } from 'react-native-reanimated';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { RequestCard, type RequestDraft } from '@/components/membership/RequestCard';
import { AppText } from '@/components/ui/AppText';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import {
  useApproveRequest,
  useBulkApprove,
  usePendingRequests,
  useRejectRequest,
} from '@/hooks/use-membership-admin';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';

const MAX_STAGGER = 8;
const EMPTY_DRAFT: RequestDraft = { chosen: null, reason: '' };

/** 수백 건이면 응답을 기다리다 끊겨도 서버는 계속 처리한다. */
function bulkErrorMessage(error: Error) {
  return error instanceof ApiError &&
    (error.code === 'CLIENT_TIMEOUT' || error.code === 'CLIENT_BAD_RESPONSE')
    ? '처리 중일 수 있어요. 잠시 뒤 목록을 새로고침해 주세요.'
    : errorMessage(error);
}

function Requests() {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  const query = usePendingRequests();
  const approve = useApproveRequest();
  const reject = useRejectRequest();
  const bulk = useBulkApprove();
  const [message, setMessage] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<number, RequestDraft>>({});
  const busy = useRef(false);

  const sending = approve.isPending || reject.isPending || bulk.isPending;
  const locked = sending || query.isRefetching;
  const anyApprovable =
    query.hasNextPage || (query.data ?? []).some((request) => request.bulkApprovable);
  const error = approve.error ?? reject.error;

  /** 지난 결과를 지우고 보낸다. */
  async function run(send: () => Promise<unknown>) {
    // 보내는 중에 지우면 그 결과와 오류를 놓친다.
    if (busy.current) {
      return false;
    }
    busy.current = true;
    approve.reset();
    reject.reset();
    bulk.reset();
    setMessage(null);
    try {
      return (await send()) !== undefined;
    } finally {
      busy.current = false;
    }
  }

  function changeDraft(id: number, change: Partial<RequestDraft>) {
    setDrafts((old) => ({ ...old, [id]: { ...(old[id] ?? EMPTY_DRAFT), ...change } }));
  }

  async function approveAll() {
    return run(async () => {
      const result = await bulk.submit();
      if (result) {
        setMessage(`${result.approvedCount}건 승인, ${result.skippedCount}건 건너뜀`);
      }
      return result;
    });
  }

  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      {error ? <ErrorBanner message={errorMessage(error)} /> : null}
      {bulk.error ? <ErrorBanner message={bulkErrorMessage(bulk.error)} /> : null}
      {message ? (
        <Animated.View entering={reduced ? undefined : ZoomIn.springify().damping(14)}>
          <AppText variant="label" accessibilityLiveRegion="polite">
            {message}
          </AppText>
        </Animated.View>
      ) : null}
      <PagedList
        query={query}
        label="인증 신청 목록"
        header={
          <View style={{ marginBottom: space(4) }}>
            <ConfirmAction
              label="일괄 승인"
              confirmLabel="일괄 승인 확정"
              message="명부 후보가 하나로 정해진 신청을 모두 승인해요. 나머지는 그대로 남아요."
              loading={bulk.isPending}
              locked={locked || !anyApprovable}
              disabled={locked || !anyApprovable}
              onConfirm={approveAll}
            />
          </View>
        }
        keyOf={(request) => request.id}
        renderItem={(request, index) => (
          // 칸이 재사용돼도 고른 명부와 사유가 다른 신청으로 옮지 않게 한다.
          <RequestCard
            key={request.id}
            request={request}
            index={Math.min(index, MAX_STAGGER)}
            sending={
              approve.isPending && approve.variables.id === request.id
                ? 'approve'
                : reject.isPending && reject.variables.id === request.id
                  ? 'reject'
                  : null
            }
            locked={locked}
            draft={drafts[request.id]}
            onDraft={(change) => changeDraft(request.id, change)}
            onApprove={(body) => void run(() => approve.submit({ id: request.id, body }))}
            onReject={(reason) => run(() => reject.submit({ id: request.id, reason }))}
          />
        )}
        empty={
          <Notice
            illustration={<FloatingIcon icon={IdentificationCardIcon} />}
            title="기다리는 신청이 없어요"
            description="동호회원 인증을 신청하면 여기에 보여요."
          />
        }
      />
    </View>
  );
}

export default function RequestsScreen() {
  return (
    <AdminScreen title="인증 심사">
      <Requests />
    </AdminScreen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
