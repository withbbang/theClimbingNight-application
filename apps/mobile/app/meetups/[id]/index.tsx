import { ScrollView, StyleSheet, View } from 'react-native';

import { useLocalSearchParams } from 'expo-router';

import { MapPinIcon } from 'phosphor-react-native/src/icons/MapPin';

import { RoleGate } from '@/components/auth/RoleGate';
import { ApplicationBar } from '@/components/meetup/ApplicationBar';
import { CapacityHolds } from '@/components/meetup/CapacityHolds';
import { MeetupNotFound } from '@/components/meetup/MeetupNotFound';
import { ParticipantList } from '@/components/meetup/ParticipantList';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { useMeetup } from '@/hooks/use-meetups';
import { useNow } from '@/hooks/use-now';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-error';
import { formatDay, formatTime, toDateKey } from '@/lib/kst';
import { meetupPhase } from '@/lib/meetup-status';
import { parseId } from '@/lib/parse-id';
import type { MeetupDetailResponse } from '@/types/api/meetup';

interface DetailProps {
  detail: MeetupDetailResponse;
  onRefresh: () => Promise<unknown>;
}

function Detail({ detail, onRefresh }: DetailProps) {
  const { colors, space } = useTheme();
  const { meetup } = detail;
  const start = Date.parse(meetup.startsAt);
  const now = useNow(meetup.endsAt ? [start, Date.parse(meetup.endsAt)] : [start]);
  const phase = meetupPhase(meetup, new Date(now));
  const started = now >= start;

  return (
    <View style={[styles.fill, { gap: space(3) }]}>
      <ScrollView
        style={styles.fill}
        contentContainerStyle={{ gap: space(6), paddingBottom: space(4) }}
        showsVerticalScrollIndicator={false}
        refreshControl={<PullToRefresh onRefresh={onRefresh} />}
      >
        <View style={{ gap: space(2) }}>
          <FadeInItem index={0}>
            <AppText variant="display" accessibilityRole="header">
              {meetup.title}
            </AppText>
          </FadeInItem>
          <FadeInItem index={1}>
            <AppText variant="title">{formatDay(toDateKey(meetup.startsAt))}</AppText>
            <AppText variant="label" tone="muted">
              {meetup.endsAt
                ? `${formatTime(meetup.startsAt)} ~ ${formatTime(meetup.endsAt)}`
                : formatTime(meetup.startsAt)}
            </AppText>
          </FadeInItem>
          {meetup.placeName ? (
            <FadeInItem index={2} style={[styles.row, { gap: space(2) }]}>
              <MapPinIcon size={20} color={colors.textMuted} weight="fill" />
              <AppText style={styles.fill}>{meetup.placeName}</AppText>
            </FadeInItem>
          ) : null}
        </View>
        <FadeInItem index={3}>
          <CapacityHolds
            confirmed={detail.confirmedCount}
            waitlist={detail.waitlistCount}
            capacity={meetup.capacity}
          />
        </FadeInItem>
        {meetup.description ? (
          <FadeInItem index={4}>
            <AppText selectable>{meetup.description}</AppText>
          </FadeInItem>
        ) : null}
        <FadeInItem index={5}>
          <ParticipantList participants={detail.participants} />
        </FadeInItem>
        {meetup.host ? (
          <FadeInItem index={6}>
            <AppText variant="caption" tone="muted">{`여는 사람 ${meetup.host.nickname}`}</AppText>
          </FadeInItem>
        ) : null}
      </ScrollView>
      <ApplicationBar detail={detail} phase={phase} started={started} />
    </View>
  );
}

export default function MeetupDetailScreen() {
  const id = parseId(useLocalSearchParams().id);
  const query = useMeetup(id);
  const missing = id === null || (query.error instanceof ApiError && query.error.status === 404);

  return (
    <Screen>
      <BackButton />
      <RoleGate role="MEMBER">
        {missing ? (
          <MeetupNotFound />
        ) : (
          <QueryState query={query}>
            {(detail) => <Detail key={id} detail={detail} onRefresh={query.refetch} />}
          </QueryState>
        )}
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
});
