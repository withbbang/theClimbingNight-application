import { useEffect } from 'react';
import { ScrollView, View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { ImageBrokenIcon } from 'phosphor-react-native/src/icons/ImageBroken';
import { LockKeyIcon } from 'phosphor-react-native/src/icons/LockKey';

import { PostCard } from '@/components/feed/PostCard';
import { PostHiddenNotice } from '@/components/feed/PostHiddenNotice';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { usePost } from '@/hooks/use-post';
import { useSession } from '@/hooks/use-session';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-error';
import { parseId } from '@/lib/parse-id';
import type { PostResponse } from '@/types/api/post';

const HIDDEN = ['HIDDEN', 'HIDDEN_PENDING'];

function Detail({ post, onRefresh }: { post: PostResponse; onRefresh: () => Promise<unknown> }) {
  const { space } = useTheme();
  const router = useRouter();
  const hidden = post.status !== null && HIDDEN.includes(post.status);
  return (
    <ScrollView
      contentContainerStyle={{ gap: space(4), paddingBottom: space(4) }}
      showsVerticalScrollIndicator={false}
      refreshControl={<PullToRefresh onRefresh={onRefresh} />}
    >
      {hidden ? <PostHiddenNotice /> : null}
      <FadeInItem index={hidden ? 1 : 0}>
        <PostCard
          post={post}
          hideMedia={hidden}
          menu
          // 지우거나 차단하거나 숨긴 글은 더 볼 수 없으니 들어온 곳으로 돌아간다.
          onGone={() => (router.canGoBack() ? router.back() : router.dismissTo('/feed'))}
        />
      </FadeInItem>
    </ScrollView>
  );
}

function PostNotFound() {
  const router = useRouter();
  return (
    <Notice
      illustration={<FloatingIcon icon={ImageBrokenIcon} />}
      title="게시물을 찾을 수 없어요"
      description="지워졌거나 볼 수 없는 글이에요."
      actionLabel="피드로"
      onAction={() => router.dismissTo('/feed')}
    />
  );
}

export default function PostDetailScreen() {
  const router = useRouter();
  const id = parseId(useLocalSearchParams().id);
  const signedIn = useSession().status === 'authenticated';
  const query = usePost(id);
  const { error } = query;
  // 비로그인은 최근 몇 장만 볼 수 있다. 그 밖의 글은 API가 AUTH_REQUIRED로 막는다.
  const needsLogin =
    !signedIn &&
    error instanceof ApiError &&
    error.status === 401 &&
    error.code === 'AUTH_REQUIRED';
  const missing = id === null || (error instanceof ApiError && error.status === 404);

  useEffect(() => {
    if (needsLogin) {
      router.push('/login');
    }
  }, [needsLogin, router]);

  return (
    <Screen>
      <BackButton />
      <View style={{ flex: 1 }}>
        {needsLogin ? (
          <Notice
            illustration={<FloatingIcon icon={LockKeyIcon} />}
            title="로그인하고 볼 수 있어요"
            description="로그인 없이는 최근 사진 몇 장만 볼 수 있어요."
            actionLabel="로그인하기"
            onAction={() => router.push('/login')}
          />
        ) : missing ? (
          <PostNotFound />
        ) : (
          <QueryState query={query}>
            {(post) => <Detail key={id} post={post} onRefresh={query.refetch} />}
          </QueryState>
        )}
      </View>
    </Screen>
  );
}
