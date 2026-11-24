import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import { useRouter } from 'expo-router';

import Animated from 'react-native-reanimated';

import { StatusBadge } from '@/components/meetup/StatusBadge';
import { AppText } from '@/components/ui/AppText';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';
import { formatDay, formatTime, toDateKey } from '@/lib/kst';
import { capacityText, meetupPhase } from '@/lib/meetup-status';
import type { MeetupSummaryResponse } from '@/types/api/meetup';

const MAX_STAGGER = 6;
/** 이보다 큰 글꼴에서는 가로로 놓으면 제목 자리가 남지 않는다. */
const STACK_FONT_SCALE = 1.5;

interface MeetupCardProps {
  meetup: MeetupSummaryResponse;
  index: number;
  /** 날짜 머리글이 없는 곳에서 켠다. */
  showDate?: boolean;
}

export function MeetupCard({ meetup, index, showDate = false }: MeetupCardProps) {
  const router = useRouter();
  const { colors, radius, space } = useTheme();
  const press = usePressScale();
  const stacked = useWindowDimensions().fontScale > STACK_FONT_SCALE;
  const phase = meetupPhase(meetup, new Date());
  const phaseLabel = {
    CANCELED: '취소된 벙',
    ONGOING: '진행 중',
    ENDED: '끝난 벙',
    UPCOMING: null,
  }[phase];
  const faded = phase === 'CANCELED' || phase === 'ENDED';

  return (
    <FadeInItem index={Math.min(index, MAX_STAGGER)} style={{ marginBottom: space(3) }}>
      <Animated.View style={press.style}>
        <Pressable
          accessibilityRole="button"
          accessibilityHint="벙 상세를 엽니다"
          onPress={() => router.push(`/meetups/${meetup.id}`)}
          {...press.handlers}
          style={{
            flexDirection: stacked ? 'column' : 'row',
            gap: space(stacked ? 2 : 4),
            padding: space(5),
            borderRadius: radius.card,
            backgroundColor: colors.surface,
          }}
        >
          <View style={styles.time}>
            {showDate ? (
              <AppText variant="caption" tone="muted">
                {formatDay(toDateKey(meetup.startsAt))}
              </AppText>
            ) : null}
            <AppText variant="title" tone={faded ? 'muted' : 'default'}>
              {formatTime(meetup.startsAt)}
            </AppText>
          </View>
          <View style={[stacked ? null : styles.body, { gap: space(1) }]}>
            <AppText
              variant="label"
              tone={faded ? 'muted' : 'default'}
              style={phase === 'CANCELED' ? styles.canceled : null}
            >
              {meetup.title}
            </AppText>
            {meetup.placeName ? <AppText tone="muted">{meetup.placeName}</AppText> : null}
            <View style={[styles.counts, { gap: space(3) }]}>
              <AppText variant="caption">
                {capacityText(meetup.confirmedCount, meetup.capacity)}
              </AppText>
              {meetup.waitlistCount > 0 ? (
                <AppText variant="caption">{`대기 ${meetup.waitlistCount}`}</AppText>
              ) : null}
              {phaseLabel ? (
                <AppText variant="caption" tone={phase === 'CANCELED' ? 'danger' : 'muted'}>
                  {phaseLabel}
                </AppText>
              ) : null}
            </View>
          </View>
          <StatusBadge status={phase === 'CANCELED' ? null : meetup.myStatus} />
        </Pressable>
      </Animated.View>
    </FadeInItem>
  );
}

const styles = StyleSheet.create({
  time: { minWidth: 68 },
  body: { flex: 1 },
  counts: { flexDirection: 'row', flexWrap: 'wrap' },
  canceled: { textDecorationLine: 'line-through' },
});
