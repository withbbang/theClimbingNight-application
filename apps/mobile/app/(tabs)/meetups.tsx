import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import Animated, { FadeInLeft, FadeInRight, useReducedMotion } from 'react-native-reanimated';

import { RoleGate } from '@/components/auth/RoleGate';
import { MeetupCalendar } from '@/components/meetup/MeetupCalendar';
import { MeetupList } from '@/components/meetup/MeetupList';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { Segments } from '@/components/ui/Segments';
import { useUpcomingMeetups } from '@/hooks/use-meetups';
import { useIsAdmin } from '@/hooks/use-role';
import { useTheme } from '@/hooks/use-theme';

const VIEWS = [
  { value: 'list', label: '목록' },
  { value: 'calendar', label: '달력' },
] as const;

function Upcoming() {
  const query = useUpcomingMeetups();
  return (
    <QueryState query={query}>
      {(meetups) => <MeetupList meetups={meetups} onRefresh={query.refetch} />}
    </QueryState>
  );
}

function Meetups() {
  const { space } = useTheme();
  const router = useRouter();
  const admin = useIsAdmin();
  const reduced = useReducedMotion();
  const [view, setView] = useState<'list' | 'calendar'>('list');

  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      <AppText variant="display" accessibilityRole="header">
        {view === 'list' ? '다가오는 벙' : '벙 달력'}
      </AppText>
      <Segments options={VIEWS} value={view} onChange={setView} />
      {admin ? (
        <Button
          label="벙 열기"
          variant="secondary"
          onPress={() => router.push('/admin/meetups/new')}
        />
      ) : null}
      <Animated.View
        key={view}
        style={styles.fill}
        entering={
          reduced ? undefined : (view === 'list' ? FadeInLeft : FadeInRight).springify().damping(20)
        }
      >
        {view === 'list' ? <Upcoming /> : <MeetupCalendar />}
      </Animated.View>
    </View>
  );
}

export default function MeetupsScreen() {
  return (
    <Screen bottomInset={false}>
      <RoleGate
        role="MEMBER"
        title="로그인하고 벙을 확인하세요"
        description="벙 일정과 신청은 동호회원에게 열려 있어요."
      >
        <Meetups />
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
