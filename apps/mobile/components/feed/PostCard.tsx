import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { ChatCircleIcon } from 'phosphor-react-native/src/icons/ChatCircle';
import { HeartIcon } from 'phosphor-react-native/src/icons/Heart';
import Animated, { FadeIn, useReducedMotion } from 'react-native-reanimated';

import { MediaCarousel } from '@/components/feed/MediaCarousel';
import { AppText } from '@/components/ui/AppText';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';
import { formatRelative } from '@/lib/kst';
import type { PostResponse } from '@/types/api/post';

const COLLAPSED_LINES = 3;
const AVATAR = 40;

/** 닉네임 첫 글자를 홀드 모양 위에 얹는다. */
function Avatar({ nickname }: { nickname: string }) {
  const { colors } = useTheme();
  return (
    <View
      style={[styles.avatar, { backgroundColor: colors.accent }]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      <AppText variant="label" tone="onAccent" style={styles.initial}>
        {nickname.slice(0, 1)}
      </AppText>
    </View>
  );
}

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

interface PostCardProps {
  post: PostResponse;
  /** 주면 작성자 줄과 사진을 눌러 상세로 간다. */
  onOpen?: () => void;
  /** 숨겨진 글처럼 사진을 보여 줄 수 없을 때. */
  hideMedia?: boolean;
}

export function PostCard({ post, onOpen, hideMedia = false }: PostCardProps) {
  const { colors, radius, space } = useTheme();
  const press = usePressScale(0.98);
  const showMedia = !hideMedia && post.media.length > 0;

  const header = (
    <View style={[styles.row, { gap: space(3), padding: space(4) }]}>
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
      {showMedia ? <MediaCarousel media={post.media} onPress={onOpen} /> : null}
      <View style={{ gap: space(2), paddingHorizontal: space(4), paddingTop: space(3) }}>
        <View
          accessible
          accessibilityLabel={`좋아요 ${post.likeCount}개, 댓글 ${post.commentCount}개`}
          style={[styles.row, { gap: space(4) }]}
        >
          <View style={[styles.row, { gap: space(1) }]}>
            <HeartIcon size={20} color={colors.text} weight="bold" />
            <AppText variant="label">{post.likeCount}</AppText>
          </View>
          <View style={[styles.row, { gap: space(1) }]}>
            <ChatCircleIcon size={20} color={colors.text} weight="bold" />
            <AppText variant="label">{post.commentCount}</AppText>
          </View>
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
  avatar: {
    width: AVATAR,
    height: AVATAR,
    alignItems: 'center',
    justifyContent: 'center',
    // FloatingIcon과 같은 홀드 모양.
    borderTopLeftRadius: 18,
    borderTopRightRadius: 13,
    borderBottomRightRadius: 20,
    borderBottomLeftRadius: 15,
  },
  initial: { fontFamily: 'Pretendard-ExtraBold' },
  measure: { position: 'absolute', left: 0, right: 0, top: 0, opacity: 0 },
  more: { alignSelf: 'flex-start' },
  moreText: { fontFamily: 'Pretendard-SemiBold' },
});
