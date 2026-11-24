import { View } from 'react-native';

import { MeetupCard } from '@/components/meetup/MeetupCard';
import { AppText } from '@/components/ui/AppText';
import { QueryState } from '@/components/ui/QueryState';
import { useUpcomingMeetups } from '@/hooks/use-meetups';
import { useNow } from '@/hooks/use-now';
import { useTheme } from '@/hooks/use-theme';
import { meetupPhase, phaseBoundaries } from '@/lib/meetup-status';
import type { MeetupSummaryResponse } from '@/types/api/meetup';

const MAX_MEETUPS = 3;

function Cards({ meetups }: { meetups: MeetupSummaryResponse[] }) {
  const now = useNow(meetups.flatMap(phaseBoundaries));
  const next = meetups
    .filter((m) => meetupPhase(m, new Date(now)) === 'UPCOMING')
    .slice(0, MAX_MEETUPS);
  return next.length === 0 ? (
    <AppText tone="muted">아직 열린 벙이 없어요</AppText>
  ) : (
    <View>
      {next.map((meetup, i) => (
        <MeetupCard key={meetup.id} meetup={meetup} index={i + 1} showDate />
      ))}
    </View>
  );
}

export function NextMeetups() {
  const { space } = useTheme();
  const query = useUpcomingMeetups();
  return (
    <View style={{ gap: space(3) }}>
      <AppText variant="title" accessibilityRole="header">
        다가오는 벙
      </AppText>
      <QueryState query={query}>{(meetups) => <Cards meetups={meetups} />}</QueryState>
    </View>
  );
}
