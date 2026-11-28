import { StyleSheet, View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { useQueryClient } from '@tanstack/react-query';
import { ImagesSquareIcon } from 'phosphor-react-native/src/icons/ImagesSquare';
import { UserCircleDashedIcon } from 'phosphor-react-native/src/icons/UserCircleDashed';
import Animated, { FadeIn, useReducedMotion } from 'react-native-reanimated';

import { RoleGate } from '@/components/auth/RoleGate';
import { InProgressPosts } from '@/components/feed/InProgressPosts';
import { ProfileGrid } from '@/components/profile/ProfileGrid';
import { ProfileHeader } from '@/components/profile/ProfileHeader';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { useSession } from '@/hooks/use-session';
import { profileKey, useUserPosts, useUserProfile } from '@/hooks/use-user-profile';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { parseId } from '@/lib/parse-id';
import type { ProfileResponse } from '@/types/api/profile';

const IN_PROGRESS = ['posts', 'mine', 'in-progress'];

function GridEmpty({
  profile,
  posts,
}: {
  profile: ProfileResponse;
  posts: ReturnType<typeof useUserPosts>;
}) {
  const router = useRouter();
  const { colors, space } = useTheme();
  if (posts.isError) {
    return (
      <View style={{ gap: space(2) }}>
        <ErrorBanner message={errorMessage(posts.error)} />
        <Button label="다시 시도" variant="secondary" onPress={() => void posts.refetch()} />
      </View>
    );
  }
  if (posts.isPending) {
    return (
      <View accessible accessibilityLabel="불러오는 중" style={{ paddingVertical: space(6) }}>
        <PulseHolds color={colors.accent} />
      </View>
    );
  }
  return profile.mine ? (
    <Notice
      illustration={<FloatingIcon icon={ImagesSquareIcon} />}
      title="첫 사진을 올려 보세요"
      description="올린 사진이 여기에 모여요."
      actionLabel="사진 올리기"
      onAction={() => router.push('/posts/new')}
    />
  ) : (
    <Notice
      illustration={<FloatingIcon icon={ImagesSquareIcon} />}
      title="아직 올린 사진이 없어요"
      description={`${profile.nickname}님이 사진을 올리면 여기에 보여요.`}
    />
  );
}

function ProfileBody({
  profile,
  onRefresh,
}: {
  profile: ProfileResponse;
  onRefresh: () => Promise<unknown>;
}) {
  const router = useRouter();
  const client = useQueryClient();
  const { space } = useTheme();
  const posts = useUserPosts(profile.id);
  const { fetchNextPage, hasNextPage, isFetching, isFetchingNextPage } = posts;

  const refreshGrid = () =>
    Promise.all([
      client.invalidateQueries({ queryKey: profileKey(profile.id) }),
      client.invalidateQueries({ queryKey: ['users', profile.id, 'posts'] }),
    ]);

  const inset = { paddingHorizontal: space(6) };
  return (
    // 격자는 화면 가장자리까지 채운다. 머리·안내만 화면 여백을 둔다.
    <View style={[styles.fill, { marginHorizontal: -space(6) }]}>
      <ProfileGrid
        posts={posts.data ?? []}
        header={
          <View style={[inset, { gap: space(4), paddingBottom: space(5) }]}>
            <ProfileHeader
              profile={profile}
              onGone={() => (router.canGoBack() ? router.back() : router.dismissTo('/feed'))}
            />
            {profile.mine ? <InProgressPosts onDone={() => void refreshGrid()} /> : null}
          </View>
        }
        empty={
          <View style={inset}>
            <GridEmpty profile={profile} posts={posts} />
          </View>
        }
        footer={
          posts.isFetchNextPageError ? (
            <View style={[inset, { gap: space(2), paddingTop: space(3) }]}>
              <ErrorBanner message={errorMessage(posts.error)} />
              <Button label="다시 시도" variant="secondary" onPress={() => void fetchNextPage()} />
            </View>
          ) : null
        }
        onEndReached={() => {
          if (hasNextPage && !isFetching && !isFetchingNextPage) {
            void fetchNextPage();
          }
        }}
        onRefresh={() =>
          Promise.all([
            onRefresh(),
            posts.refetch(),
            profile.mine ? client.invalidateQueries({ queryKey: IN_PROGRESS }) : null,
          ])
        }
      />
    </View>
  );
}

function Profile({ id }: { id: number | null }) {
  const reduced = useReducedMotion();
  const { space } = useTheme();
  const query = useUserProfile(id);
  const missing = id === null || (query.error instanceof ApiError && query.error.status === 404);

  return (
    <View style={styles.fill}>
      <View style={[styles.bar, { gap: space(1) }]}>
        <BackButton />
        {query.data && !missing ? (
          <Animated.View entering={reduced ? undefined : FadeIn.duration(220)} style={styles.fill}>
            <AppText variant="label" numberOfLines={1}>
              {query.data.nickname}
            </AppText>
          </Animated.View>
        ) : null}
      </View>
      {missing ? (
        <Notice
          illustration={<FloatingIcon icon={UserCircleDashedIcon} />}
          title="없는 사용자예요"
          description="탈퇴했거나 볼 수 없는 사용자예요."
        />
      ) : (
        <QueryState query={query}>
          {(profile) => (
            <ProfileBody key={profile.id} profile={profile} onRefresh={query.refetch} />
          )}
        </QueryState>
      )}
    </View>
  );
}

export default function UserProfileScreen() {
  const params = useLocalSearchParams();
  const id = parseId(params.id);
  const signedIn = useSession().status === 'authenticated';
  return (
    // 격자가 아래 끝까지 이어지도록 아래 여백은 목록이 맡는다.
    <Screen bottomInset={false}>
      {signedIn ? null : <BackButton />}
      <RoleGate role="USER">
        <Profile id={id} />
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  bar: { flexDirection: 'row', alignItems: 'center' },
});
