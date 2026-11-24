import { useEffect } from 'react';
import { View } from 'react-native';

import { useRouter } from 'expo-router';

import Animated, { FadeInDown, LinearTransition, useReducedMotion } from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Hold } from '@/components/ui/Hold';
import { useApply } from '@/hooks/use-meetup-application';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { MeetupPhase } from '@/lib/meetup-status';
import type { MeetupDetailResponse } from '@/types/api/meetup';

function statusText({ my }: MeetupDetailResponse, phase: MeetupPhase, started: boolean) {
  if (phase === 'CANCELED') {
    return '취소된 벙이에요';
  }
  switch (my?.status) {
    case 'CONFIRMED':
      return '참여가 확정됐어요';
    case 'WAITLISTED':
      return my.waitlistPosition === null ? '대기 중이에요' : `대기 ${my.waitlistPosition}번`;
    case 'REMOVED':
      return '운영진이 내보낸 벙이에요';
    default:
      return started ? (phase === 'ENDED' ? '끝난 벙이에요' : '이미 시작한 벙이에요') : null;
  }
}

interface ApplicationBarProps {
  detail: MeetupDetailResponse;
  phase: MeetupPhase;
  started: boolean;
}

/** 화면 아래에 붙는 내 상태와 신청·취소 버튼. */
export function ApplicationBar({ detail, phase, started }: ApplicationBarProps) {
  const router = useRouter();
  const { colors, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const { meetup, my } = detail;
  const apply = useApply(meetup.id);
  const status = my?.status ?? null;
  const active = status === 'CONFIRMED' || status === 'WAITLISTED';
  const canApply = phase === 'UPCOMING' && !active && status !== 'REMOVED';
  const canCancel = active && !started;
  const { isError, reset } = apply;

  // 다시 받은 상태가 신청 결과를 말해 주면 지난 오류는 지운다.
  useEffect(() => {
    if (!canApply && isError) {
      reset();
    }
  }, [canApply, isError, reset]);

  const text = statusText(detail, phase, started);

  return (
    <Animated.View
      layout={reduced ? undefined : LinearTransition.springify().damping(18)}
      style={{
        gap: space(3),
        padding: space(4),
        borderRadius: radius.card,
        backgroundColor: colors.surface,
      }}
    >
      {apply.error ? <ErrorBanner message={errorMessage(apply.error)} /> : null}
      {text ? (
        <Animated.View
          key={text}
          entering={reduced ? undefined : FadeInDown.springify().damping(14)}
          accessibilityLiveRegion="polite"
          style={{ flexDirection: 'row', alignItems: 'center', gap: space(3) }}
        >
          {active && phase !== 'CANCELED' ? <Hold on={status === 'CONFIRMED'} size={20} /> : null}
          <View style={{ flex: 1 }}>
            <AppText variant="label" tone={phase === 'CANCELED' ? 'danger' : 'default'}>
              {text}
            </AppText>
          </View>
        </Animated.View>
      ) : null}
      {canApply ? (
        <Button label="신청하기" loading={apply.isPending} onPress={() => void apply.submit('')} />
      ) : null}
      {canCancel ? (
        <Button
          label="신청 취소"
          variant="secondary"
          onPress={() => router.push(`/meetups/${meetup.id}/cancel`)}
        />
      ) : null}
    </Animated.View>
  );
}
