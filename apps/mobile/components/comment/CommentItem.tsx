import { useEffect, useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { CaretDownIcon } from 'phosphor-react-native/src/icons/CaretDown';
import { DotsThreeIcon } from 'phosphor-react-native/src/icons/DotsThree';
import Animated, {
  FadeIn,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { CommentMenu } from '@/components/comment/CommentMenu';
import { ReplyList } from '@/components/comment/ReplyList';
import { AppText } from '@/components/ui/AppText';
import { AuthorLink } from '@/components/ui/AuthorLink';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';
import { formatRelative } from '@/lib/kst';
import type { CommentResponse } from '@/types/api/comment';

interface CommentItemProps {
  postId: number;
  comment: CommentResponse;
  /** 오른쪽 끝 자리. */
  trailing?: ReactNode;
  /** ⋯ 메뉴(삭제·신고·차단)를 붙인다. */
  menu?: boolean;
  /** 주면 "답글" 단추가 생긴다. */
  onReply?: (comment: CommentResponse) => void;
  /** 답글 펼침을 부모가 쥘 때. 없으면 스스로 쥔다. */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

/** 댓글 줄의 ⋯ 단추. 누르면 점이 90도 돈다. */
function MenuButton({
  nickname,
  open,
  onToggle,
}: {
  nickname: string;
  open: boolean;
  onToggle: () => void;
}) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const press = usePressScale(0.9);
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
        accessibilityLabel={`${nickname}의 댓글 메뉴`}
        accessibilityState={{ expanded: open }}
        hitSlop={8}
        onPress={onToggle}
        {...press.handlers}
        style={styles.menuButton}
      >
        <Animated.View style={spin}>
          <DotsThreeIcon size={20} color={colors.textMuted} weight="bold" />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

/** 답글 줄 위에서 접고 펼치는 버튼. 화살표가 돌며 상태를 알린다. */
function RepliesToggle({
  count,
  open,
  onToggle,
}: {
  count: number;
  open: boolean;
  onToggle: () => void;
}) {
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  const press = usePressScale(0.96);
  const turn = useSharedValue(open ? 1 : 0);

  useEffect(() => {
    const target = open ? 1 : 0;
    turn.set(reduced ? target : withSpring(target, motion.spring));
  }, [motion.spring, open, reduced, turn]);

  const spin = useAnimatedStyle(() => ({ transform: [{ rotate: `${turn.get() * 180}deg` }] }));

  return (
    <Animated.View style={[press.style, styles.toggle]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={open ? '답글 접기' : `답글 ${count}개 보기`}
        accessibilityState={{ expanded: open }}
        hitSlop={space(2)}
        onPress={onToggle}
        {...press.handlers}
        style={[styles.row, { gap: space(1), minHeight: 44 }]}
      >
        <AppText variant="caption" tone="muted" style={styles.strong}>
          {open ? '답글 접기' : `답글 ${count}개 보기`}
        </AppText>
        <Animated.View style={spin}>
          <CaretDownIcon size={14} color={colors.textMuted} weight="bold" />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

/** 댓글 한 줄. 삭제·숨김은 자리표시만, 최상위 댓글은 답글을 펼칠 수 있다. */
export function CommentItem({
  postId,
  comment,
  trailing,
  menu = false,
  onReply,
  open: controlledOpen,
  onOpenChange,
}: CommentItemProps) {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  const [ownOpen, setOwnOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const open = controlledOpen ?? ownOpen;
  const setOpen = (value: boolean) => (onOpenChange ? onOpenChange(value) : setOwnOpen(value));
  const isReply = comment.parentId !== null;

  const placeholder = comment.display === 'DELETED' || comment.display === 'HIDDEN';
  const hiddenMine = comment.display === 'HIDDEN_MINE';
  const nickname = comment.author?.nickname ?? '';

  return (
    <View style={{ gap: space(1) }}>
      {placeholder ? (
        // 지워지거나 숨겨져도 그 아래 보이는 답글은 남는다.
        <View style={[styles.row, { paddingVertical: space(2) }]}>
          <AppText tone="muted">
            {comment.display === 'DELETED' ? '삭제된 댓글입니다' : '숨겨진 댓글입니다'}
          </AppText>
        </View>
      ) : (
        <View style={[styles.row, styles.top, { gap: space(3) }]}>
          <AuthorLink
            userId={comment.author?.id ?? 0}
            nickname={nickname}
            imageUrl={comment.author?.profileImageUrl ?? null}
            withdrawn={comment.author === null}
          />
          <View style={styles.fill}>
            <View style={[styles.row, { gap: space(2) }]}>
              <AuthorLink
                userId={comment.author?.id ?? 0}
                nickname={nickname}
                imageUrl={comment.author?.profileImageUrl ?? null}
                withdrawn={comment.author === null}
              >
                <AppText variant="label" numberOfLines={1} style={styles.shrink}>
                  {nickname}
                </AppText>
              </AuthorLink>
              <AppText variant="caption" tone="muted">
                {formatRelative(comment.createdAt)}
              </AppText>
            </View>
            {isReply ? (
              <AppText selectable>
                <AppText tone="muted" style={styles.strong}>
                  {`@${comment.replyToNickname ?? '알 수 없음'}`}
                </AppText>
                {` ${comment.body}`}
              </AppText>
            ) : (
              <AppText tone={hiddenMine ? 'muted' : 'default'} selectable>
                {comment.body}
              </AppText>
            )}
            {hiddenMine ? (
              <Animated.View entering={reduced ? undefined : FadeIn.duration(220)}>
                <AppText variant="caption" tone="muted" style={{ marginTop: space(1) }}>
                  운영 정책에 따라 숨겨졌어요
                </AppText>
              </Animated.View>
            ) : null}
          </View>
          {menu ? (
            <MenuButton
              nickname={nickname}
              open={menuOpen}
              onToggle={() => setMenuOpen((value) => !value)}
            />
          ) : null}
          {trailing}
        </View>
      )}
      {menu && menuOpen && !placeholder ? (
        <Animated.View
          entering={reduced ? undefined : FadeIn.duration(180)}
          style={{ paddingLeft: space(13) }}
        >
          <CommentMenu postId={postId} comment={comment} onClose={() => setMenuOpen(false)} />
        </Animated.View>
      ) : null}
      {onReply && comment.display === 'VISIBLE' ? (
        <View style={{ paddingLeft: space(13) }}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${nickname}의 댓글에 답글`}
            hitSlop={space(2)}
            onPress={() => onReply(comment)}
            style={{ minHeight: 32, justifyContent: 'center', alignSelf: 'flex-start' }}
          >
            <AppText variant="caption" tone="muted" style={styles.strong}>
              답글 달기
            </AppText>
          </Pressable>
        </View>
      ) : null}
      {!isReply && comment.replyCount > 0 ? (
        <View style={{ paddingLeft: space(13) }}>
          <RepliesToggle count={comment.replyCount} open={open} onToggle={() => setOpen(!open)} />
        </View>
      ) : null}
      {open ? (
        <ReplyList postId={postId} parentId={comment.id} menu={menu} onReply={onReply} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
  top: { alignItems: 'flex-start' },
  fill: { flex: 1 },
  shrink: { flexShrink: 1 },
  toggle: { alignSelf: 'flex-start' },
  menuButton: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  strong: { fontFamily: 'Pretendard-SemiBold' },
});
