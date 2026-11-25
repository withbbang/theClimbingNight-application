import { ScrollView, View } from 'react-native';

import { useRouter } from 'expo-router';

import { useQueryClient } from '@tanstack/react-query';

import { HomeHeader } from '@/components/home/HomeHeader';
import { JoinBanner } from '@/components/home/JoinBanner';
import { NextMeetups } from '@/components/home/NextMeetups';
import { RecentNotices } from '@/components/home/RecentNotices';
import { Button } from '@/components/ui/Button';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { Screen } from '@/components/ui/Screen';
import { useIsMember } from '@/hooks/use-role';
import { useTheme } from '@/hooks/use-theme';

const REFRESH_KEYS = [['meetups'], ['notices'], ['app-settings']];

export default function HomeScreen() {
  const { space } = useTheme();
  const router = useRouter();
  const client = useQueryClient();
  const isMember = useIsMember();

  return (
    <Screen bottomInset={false}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ gap: space(8), paddingBottom: space(6) }}
        refreshControl={
          <PullToRefresh
            onRefresh={() =>
              Promise.all(REFRESH_KEYS.map((queryKey) => client.invalidateQueries({ queryKey })))
            }
          />
        }
      >
        <HomeHeader />
        {isMember ? (
          <NextMeetups />
        ) : (
          <View style={{ gap: space(3) }}>
            <JoinBanner />
            <Button label="동호회 소개" variant="secondary" onPress={() => router.push('/about')} />
          </View>
        )}
        <RecentNotices />
      </ScrollView>
    </Screen>
  );
}
