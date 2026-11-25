import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { AttendanceSwitch } from '@/components/meetup/AttendanceSwitch';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { FadeInItem } from '@/components/ui/FadeInItem';
import type { ApplicationAction } from '@/hooks/use-application-actions';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/kst';
import type { MeetupPhase } from '@/lib/meetup-status';
import type { MeetupApplicationAdminResponse } from '@/types/api/meetup';

interface ApplicationRowProps {
  application: MeetupApplicationAdminResponse;
  phase: MeetupPhase;
  index: number;
  /** 이 신청자에게 보내는 중인 동작. */
  sending: ApplicationAction | undefined;
  /** 성공하면 true. */
  onSend: (action: ApplicationAction) => Promise<boolean>;
}

export function ApplicationRow({
  application,
  phase,
  index,
  sending,
  onSend,
}: ApplicationRowProps) {
  const router = useRouter();
  const { colors, radius, space } = useTheme();
  const { somoimNickname: name, status, attendanceId, attendanceResult } = application;
  const active = status === 'CONFIRMED' || status === 'WAITLISTED';

  return (
    <FadeInItem
      index={index}
      style={{
        gap: space(3),
        marginBottom: space(3),
        padding: space(4),
        borderRadius: radius.card,
        backgroundColor: colors.surface,
      }}
    >
      <View style={{ gap: space(1) }}>
        <AppText variant="label">{name}</AppText>
        {application.penaltyTotal > 0 ? (
          <AppText variant="caption">
            {`블랙리스트 ${application.penaltyTotal}회 (최근 3개월 ${application.penaltyRecent}회)`}
          </AppText>
        ) : null}
        {status === 'REMOVED' ? <AppText tone="muted">내보냄</AppText> : null}
        {application.canceledAt && !active ? (
          <AppText variant="caption" tone="muted">
            {formatDateTime(application.canceledAt)}
          </AppText>
        ) : null}
        {application.lateCancel ? <AppText tone="danger">늦은 취소</AppText> : null}
        {application.cancelReason ? (
          <AppText selectable>{`사유: ${application.cancelReason}`}</AppText>
        ) : null}
        {application.lateCancelPenalized ? (
          <AppText variant="caption">블랙리스트에 기록했어요</AppText>
        ) : null}
      </View>
      {attendanceId !== null && attendanceResult !== null ? (
        <AttendanceSwitch
          target={name}
          value={sending?.type === 'correct' ? sending.result : attendanceResult}
          loading={sending?.type === 'correct'}
          onChange={(result) => onSend({ type: 'correct', attendanceId, result })}
        />
      ) : phase === 'ENDED' && status === 'CONFIRMED' ? (
        <AppText tone="muted">참석 기록을 준비하고 있어요</AppText>
      ) : null}
      {application.lateCancel && !application.lateCancelPenalized && phase !== 'CANCELED' ? (
        <ConfirmAction
          target={name}
          label="블랙리스트에 기록"
          confirmLabel="기록 확정"
          message="늦은 취소로 블랙리스트에 1회 기록돼요."
          loading={sending?.type === 'penalize'}
          locked={sending !== undefined}
          disabled={sending !== undefined}
          onConfirm={() => onSend({ type: 'penalize' })}
        />
      ) : null}
      {active && phase === 'UPCOMING' ? (
        <ConfirmAction
          target={name}
          label="내보내기"
          confirmLabel="내보내기 확정"
          message="내보내면 이 벙에 다시 신청할 수 없어요."
          loading={sending?.type === 'remove'}
          locked={sending !== undefined}
          disabled={sending !== undefined}
          onConfirm={() => onSend({ type: 'remove' })}
        />
      ) : null}
      <Button
        label="블랙리스트 보기"
        accessibilityLabel={`${name} 블랙리스트 보기`}
        variant="text"
        onPress={() => router.push(`/admin/penalties/${application.rosterMemberId}`)}
      />
    </FadeInItem>
  );
}
