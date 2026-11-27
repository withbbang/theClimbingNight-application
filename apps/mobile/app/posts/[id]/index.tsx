import { useEffect, useRef } from 'react';
import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { FlashList, type FlashListRef } from '@shopify/flash-list';
import { ImageBrokenIcon } from 'phosphor-react-native/src/icons/ImageBroken';
import { LockKeyIcon } from 'phosphor-react-native/src/icons/LockKey';

import { CommentItem } from '@/components/comment/CommentItem';
import { LikeCountLink } from '@/components/feed/LikeCountLink';
import { PostCard } from '@/components/feed/PostCard';
import { PostHiddenNotice } from '@/components/feed/PostHiddenNotice';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Notice } from '@/components/ui/Notice';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { useComments } from '@/hooks/use-comments';
import { usePost } from '@/hooks/use-post';
import { useSession } from '@/hooks/use-session';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { parseId } from '@/lib/parse-id';
import type { CommentResponse } from '@/types/api/comment';
import type { PostResponse } from '@/types/api/post';

const HIDDEN = ['HIDDEN', 'HIDDEN_PENDING'];

function Detail({
  post,
  focusComments,
  onRefresh,
}: {
  post: PostResponse;
  focusComments: boolean;
  onRefresh: () => Promise<unknown>;
}) {
  const { space } = useTheme();
  const router = useRouter();
  const signedIn = useSession().status === 'authenticated';
  const listRef = useRef<FlashListRef<CommentResponse>>(null);
  const focused = useRef(false);
  const hidden = post.status !== null && HIDDEN.includes(post.status);
  // 비로그인은 댓글을 요청하지 않는다. 숨겨진 내 글의 기존 댓글은 볼 수 있고, 볼 수 있는지는 API가 판단한다.
  const query = useComments(post.id, signedIn);
  const { data: comments, fetchNextPage, hasNextPage, isFetching, isFetchingNextPage } = query;

  // 댓글 단추나 focus=comments로 들어오면 첫 댓글로 내린다. 댓글이 없으면 맨 아래로 간다.
  const scrollToComments = () => {
    if (comments && comments.length > 0) {
      void listRef.current?.scrollToIndex({ index: 0, animated: true });
    } else {
      listRef.current?.scrollToEnd({ animated: true });
    }
  };
  // 비로그인은 댓글을 받지 않으니 로그인 안내 자리로 바로 간다.
  const loaded = comments !== undefined || !signedIn;
  useEffect(() => {
    if (focusComments && loaded && !focused.current) {
      focused.current = true;
      scrollToComments();
    }
    // 처음 받은 뒤 한 번만 간다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusComments, loaded]);

  const empty = !signedIn ? (
    <Notice
      illustration={<FloatingIcon icon={LockKeyIcon} />}
      title="로그인하고 댓글을 볼 수 있어요"
      description="댓글은 로그인한 뒤에 읽고 남길 수 있어요."
      actionLabel="로그인하기"
      onAction={() => router.push('/login')}
    />
  ) : query.isError ? (
    <View style={{ gap: space(2) }}>
      <ErrorBanner message={errorMessage(query.error)} />
      <Button label="다시 시도" variant="secondary" onPress={() => void query.refetch()} />
    </View>
  ) : query.isPending ? null : (
    <AppText tone="muted">아직 댓글이 없어요</AppText>
  );

  return (
    <FlashList
      ref={listRef}
      accessibilityLabel="댓글"
      data={signedIn ? (comments ?? []) : []}
      keyExtractor={(comment) => String(comment.id)}
      renderItem={({ item }) => (
        <View style={{ paddingBottom: space(4) }}>
          <CommentItem key={item.id} postId={post.id} comment={item} />
        </View>
      )}
      onEndReached={() => {
        if (hasNextPage && !isFetching && !isFetchingNextPage) {
          void fetchNextPage();
        }
      }}
      ListHeaderComponent={
        <View style={{ gap: space(4), paddingBottom: space(4) }}>
          {hidden ? <PostHiddenNotice /> : null}
          <FadeInItem index={hidden ? 1 : 0}>
            <PostCard
              post={post}
              hideMedia={hidden}
              menu
              // 지우거나 차단하거나 숨긴 글은 더 볼 수 없으니 들어온 곳으로 돌아간다.
              onGone={() => (router.canGoBack() ? router.back() : router.dismissTo('/feed'))}
              onCommentPress={scrollToComments}
            />
          </FadeInItem>
          <LikeCountLink postId={post.id} count={post.likeCount} />
        </View>
      }
      ListEmptyComponent={empty}
      ListFooterComponent={
        query.isFetchNextPageError ? (
          <Button label="다시 시도" variant="secondary" onPress={() => void fetchNextPage()} />
        ) : null
      }
      contentContainerStyle={{ paddingBottom: space(4) }}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
      refreshControl={
        <PullToRefresh
          onRefresh={() => Promise.all([onRefresh(), signedIn ? query.refetch() : null])}
        />
      }
    />
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
  const params = useLocalSearchParams();
  const id = parseId(params.id);
  const focusComments = params.focus === 'comments';
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
            {(post) => (
              <Detail
                key={id}
                post={post}
                focusComments={focusComments}
                onRefresh={query.refetch}
              />
            )}
          </QueryState>
        )}
      </View>
    </Screen>
  );
}
