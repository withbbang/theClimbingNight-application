import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { MenuRow } from '@/components/my/MenuRow';
import { AppText } from '@/components/ui/AppText';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { useCancelMeetup } from '@/hooks/use-meetup-admin';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { MeetupPhase } from '@/lib/meetup-status';
import type { MeetupResponse } from '@/types/api/meetup';

interface AdminBarProps {
  meetup: MeetupResponse;
  phase: MeetupPhase;
  started: boolean;
}

export function AdminBar({ meetup, phase, started }: AdminBarProps) {
  const router = useRouter();
  const { space } = useTheme();
  const cancel = useCancelMeetup(meetup.id);
  const canCancel = phase === 'UPCOMING' || phase === 'ONGOING';
  const base = `/admin/meetups/${meetup.id}` as const;

  return (
    <View style={{ gap: space(2) }}>
      <FadeInItem index={7}>
        <AppText variant="label" accessibilityRole="header">
          운영진
        </AppText>
      </FadeInItem>
      <View>
        <MenuRow
          label="신청자 관리"
          index={8}
          onPress={() => router.push(`${base}/applications`)}
        />
        {phase === 'UPCOMING' && !started ? (
          <MenuRow label="벙 수정" index={9} onPress={() => router.push(`${base}/edit`)} />
        ) : null}
        {/* 가져온 벙은 정원이 없어 복제할 수 없다. */}
        {meetup.capacity === null ? null : (
          <MenuRow label="벙 복제" index={10} onPress={() => router.push(`${base}/copy`)} />
        )}
      </View>
      {canCancel ? (
        <FadeInItem index={11}>
          <ConfirmAction
            label="벙 취소"
            confirmLabel="벙 취소 확정"
            message="신청한 사람 모두에게 취소 알림이 가고 되돌릴 수 없어요."
            loading={cancel.isPending}
            error={cancel.error ? errorMessage(cancel.error) : null}
            onConfirm={async () => (await cancel.submit()) !== undefined}
          />
        </FadeInItem>
      ) : null}
    </View>
  );
}
