import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { ChatCircleIcon } from 'phosphor-react-native/src/icons/ChatCircle';
import { DotsThreeIcon } from 'phosphor-react-native/src/icons/DotsThree';
import Animated, {
  FadeIn,
  FadeInDown,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { LikeButton } from '@/components/feed/LikeButton';
import { MediaCarousel } from '@/components/feed/MediaCarousel';
import { PostMenu } from '@/components/feed/PostMenu';
import { AppText } from '@/components/ui/AppText';
import { Avatar } from '@/components/ui/Avatar';
import { useLikePost } from '@/hooks/use-like-post';
import { usePressScale } from '@/hooks/use-press-scale';
import { useSession } from '@/hooks/use-session';
import { useTheme } from '@/hooks/use-theme';
import { formatRelative } from '@/lib/kst';
import type { PostResponse } from '@/types/api/post';

const COLLAPSED_LINES = 3;

/** 세 줄이 넘으면 접어 두고 "더 보기"로 편다. 줄 수는 보이지 않는 전체 본문으로 잰다. */
function PostBody({ body }: { body: string }) {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  const [overflow, setOverflow] = useState(false);
  const [expanded, setExpanded] = useState(false);

  return (
    <View>
      <Animated.View key={String(expanded)} entering={reduced ? undefined : FadeIn.duration(220)}>
        <AppText numberOfLines={expanded ? undefined : COLLAPSED_LINES} selectable={expanded}>
          {body}
        </AppText>
      </Animated.View>
      {expanded ? null : (
        <AppText
          testID="post-body-measure"
          pointerEvents="none"
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          onTextLayout={(event) => setOverflow(event.nativeEvent.lines.length > COLLAPSED_LINES)}
          style={styles.measure}
        >
          {body}
        </AppText>
      )}
      {overflow && !expanded ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="더 보기"
          hitSlop={space(3)}
          onPress={() => setExpanded(true)}
          style={[styles.more, { paddingTop: space(1) }]}
        >
          <AppText variant="caption" tone="muted" style={styles.moreText}>
            더 보기
          </AppText>
        </Pressable>
      ) : null}
    </View>
  );
}

/** ⋯ 버튼. 펼치면 점이 세로로 돈다. 비로그인은 로그인 화면으로 보낸다. */
function MenuButton({
  nickname,
  open,
  onToggle,
}: {
  nickname: string;
  open: boolean;
  onToggle: () => void;
}) {
  const router = useRouter();
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const signedIn = useSession().status === 'authenticated';
  const press = usePressScale(0.85);
  const turn = useSharedValue(open ? 1 : 0);

  useEffect(() => {
    const target = open ? 1 : 0;
    turn.set(reduced ? target : withSpring(target, motion.spring));
  }, [motion.spring, open, reduced, turn]);

  const spin = useAnimatedStyle(() => ({ transform: [{ rotate: `${turn.get() * 90}deg` }] }));

  return (
    <Animated.View style={press.style}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${nickname}의 게시물 메뉴`}
        accessibilityState={{ expanded: open }}
        hitSlop={8}
        onPress={() => (signedIn ? onToggle() : router.push('/login'))}
        {...press.handlers}
        style={styles.menuButton}
      >
        <Animated.View style={spin}>
          <DotsThreeIcon size={24} color={colors.text} weight="bold" />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

interface PostCardProps {
  post: PostResponse;
  /** 주면 작성자 줄과 사진을 눌러 상세로 간다. */
  onOpen?: () => void;
  /** 숨겨진 글처럼 사진을 보여 줄 수 없을 때. */
  hideMedia?: boolean;
  /** ⋯ 메뉴(수정·삭제, 신고·차단·숨기기)를 붙인다. */
  menu?: boolean;
  /** 메뉴로 지우거나 차단하거나 숨겨서 이 글을 더 볼 수 없게 됐을 때. */
  onGone?: () => void;
  /** 주면 댓글 버튼이 상세로 가지 않고 이 함수를 부른다. 이미 상세에 있을 때 쓴다. */
  onCommentPress?: () => void;
}

export function PostCard({
  post,
  onOpen,
  hideMedia = false,
  menu = false,
  onGone,
  onCommentPress,
}: PostCardProps) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const router = useRouter();
  const press = usePressScale(0.98);
  const commentPress = usePressScale(0.88);
  const signedIn = useSession().status === 'authenticated';
  const { setLiked } = useLikePost();
  const [celebrate, setCelebrate] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const showMedia = !hideMedia && post.media.length > 0;
  // status는 작성자 본인에게만 있다. 보이지 않는 내 글은 좋아요를 켤 수 없고 끌 수만 있다.
  const likeBlocked = post.status !== null && post.status !== 'VISIBLE';

  const header = (
    <View
      style={[
        styles.row,
        { gap: space(3), padding: space(4), paddingRight: menu ? space(14) : space(4) },
      ]}
    >
      <Avatar nickname={post.author.nickname} />
      <View style={styles.fill}>
        <AppText variant="label" numberOfLines={1}>
          {post.author.nickname}
        </AppText>
        {post.publishedAt ? (
          <AppText variant="caption" tone="muted">
            {formatRelative(post.publishedAt)}
          </AppText>
        ) : null}
      </View>
    </View>
  );

  const menuButton = menu ? (
    <View style={[styles.menuSlot, { top: space(4), right: space(3) }]}>
      <MenuButton
        nickname={post.author.nickname}
        open={menuOpen}
        onToggle={() => setMenuOpen((open) => !open)}
      />
    </View>
  ) : null;

  return (
    <View
      style={[
        styles.card,
        { backgroundColor: colors.surface, borderRadius: radius.card, paddingBottom: space(4) },
      ]}
    >
      {onOpen ? (
        <Animated.View style={press.style}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${post.author.nickname}의 게시물 열기`}
            onPress={onOpen}
            {...press.handlers}
          >
            {header}
          </Pressable>
        </Animated.View>
      ) : (
        header
      )}
      {menuButton}
      {menuOpen ? (
        <Animated.View
          entering={
            reduced
              ? undefined
              : FadeInDown.springify()
                  .damping(motion.spring.damping)
                  .stiffness(motion.spring.stiffness)
          }
          style={{ paddingHorizontal: space(4), paddingBottom: space(4) }}
        >
          <PostMenu post={post} onClose={() => setMenuOpen(false)} onGone={onGone} />
        </Animated.View>
      ) : null}
      {showMedia ? (
        <MediaCarousel
          media={post.media}
          onPress={onOpen}
          onDoubleTap={
            signedIn && !likeBlocked
              ? () => {
                  setLiked(post, true);
                  setCelebrate((n) => n + 1);
                }
              : undefined
          }
        />
      ) : null}
      <View style={{ gap: space(2), paddingHorizontal: space(4), paddingTop: space(3) }}>
        <View style={[styles.row, { gap: space(4) }]}>
          <LikeButton
            key={post.id}
            celebrate={celebrate}
            liked={post.likedByMe}
            count={post.likeCount}
            disabled={likeBlocked}
            onPress={() => {
              if (!signedIn) {
                router.push('/login');
              } else {
                setLiked(post, !post.likedByMe);
              }
            }}
          />
          <Animated.View style={commentPress.style}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`댓글 ${post.commentCount}개`}
              hitSlop={space(2)}
              onPress={onCommentPress ?? (() => router.push(`/posts/${post.id}?focus=comments`))}
              {...commentPress.handlers}
              style={[styles.row, { gap: space(1) }]}
            >
              <ChatCircleIcon size={22} color={colors.text} weight="bold" />
              <AppText variant="label">{post.commentCount}</AppText>
            </Pressable>
          </Animated.View>
        </View>
        {/* 본문이 바뀌면 접힘과 줄 수를 다시 잰다. */}
        {post.body ? <PostBody key={post.body} body={post.body} /> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  card: { overflow: 'hidden' },
  row: { flexDirection: 'row', alignItems: 'center' },
  measure: { position: 'absolute', left: 0, right: 0, top: 0, opacity: 0 },
  more: { alignSelf: 'flex-start' },
  menuSlot: { position: 'absolute' },
  menuButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  moreText: { fontFamily: 'Pretendard-SemiBold' },
});
