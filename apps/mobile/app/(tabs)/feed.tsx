import { Pressable, StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { ImagesSquareIcon } from 'phosphor-react-native/src/icons/ImagesSquare';
import { PlusIcon } from 'phosphor-react-native/src/icons/Plus';
import Animated, { useReducedMotion, ZoomIn } from 'react-native-reanimated';

import { InProgressPosts } from '@/components/feed/InProgressPosts';
import { LoginToSeeMore } from '@/components/feed/LoginToSeeMore';
import { PostCard } from '@/components/feed/PostCard';
import { AppText } from '@/components/ui/AppText';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { Screen } from '@/components/ui/Screen';
import { useFeed } from '@/hooks/use-feed';
import { usePressScale } from '@/hooks/use-press-scale';
import { useSession } from '@/hooks/use-session';
import { useTheme } from '@/hooks/use-theme';

/** 비로그인에게 API가 보여 주는 최근 글 수. */
const GUEST_PREVIEW = 3;
/** 처음 화면에 들어오는 카드만 차례로 올라온다. 스크롤로 만나는 카드는 바로 올라온다. */
const STAGGERED = 4;

function FeedEmpty({ signedIn }: { signedIn: boolean }) {
  const router = useRouter();
  return (
    <Notice
      illustration={<FloatingIcon icon={ImagesSquareIcon} />}
      title="아직 올라온 사진이 없어요"
      description={
        signedIn ? '오늘 오른 벽을 처음으로 올려 보세요.' : '로그인하면 사진을 올릴 수 있어요.'
      }
      actionLabel={signedIn ? '사진 올리기' : undefined}
      onAction={signedIn ? () => router.push('/posts/new') : undefined}
    />
  );
}

/** 오른쪽 위의 올리기 버튼. 홀드 모양으로 튀어나온다. */
function NewPostButton() {
  const router = useRouter();
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const press = usePressScale(0.9);
  return (
    <Animated.View
      entering={
        reduced
          ? undefined
          : ZoomIn.springify().damping(motion.spring.damping).stiffness(motion.spring.stiffness)
      }
      style={press.style}
    >
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="사진 올리기"
        hitSlop={8}
        onPress={() => router.push('/posts/new')}
        {...press.handlers}
        style={[styles.add, { backgroundColor: colors.accent }]}
      >
        <PlusIcon size={24} color={colors.onAccent} weight="bold" />
      </Pressable>
    </Animated.View>
  );
}

export default function FeedScreen() {
  const { space } = useTheme();
  const router = useRouter();
  const signedIn = useSession().status === 'authenticated';
  const query = useFeed();
  const preview = !signedIn && (query.data?.length ?? 0) >= GUEST_PREVIEW;

  return (
    <Screen bottomInset={false}>
      <View style={[styles.fill, { gap: space(4) }]}>
        <View style={styles.header}>
          <AppText variant="display" accessibilityRole="header">
            피드
          </AppText>
          {signedIn ? <NewPostButton /> : null}
        </View>
        {signedIn ? <InProgressPosts /> : null}
        <PagedList
          query={query}
          label="피드 게시물"
          keyOf={(post) => post.id}
          canLoadMore={signedIn}
          renderItem={(post, index) => (
            <FadeInItem
              key={post.id}
              index={index < STAGGERED ? index : 0}
              style={{ marginBottom: space(4) }}
            >
              <PostCard post={post} onOpen={() => router.push(`/posts/${post.id}`)} />
            </FadeInItem>
          )}
          footer={preview ? <LoginToSeeMore /> : null}
          empty={<FeedEmpty signedIn={signedIn} />}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  add: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    // FloatingIcon과 같은 홀드 모양.
    borderTopLeftRadius: 20,
    borderTopRightRadius: 14,
    borderBottomRightRadius: 22,
    borderBottomLeftRadius: 16,
  },
});
