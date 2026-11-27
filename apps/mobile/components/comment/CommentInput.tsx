import { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';

import { PaperPlaneTiltIcon } from 'phosphor-react-native/src/icons/PaperPlaneTilt';
import { XIcon } from 'phosphor-react-native/src/icons/X';
import Animated, {
  FadeInDown,
  FadeOutDown,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useCreateComment } from '@/hooks/use-comments';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { CommentResponse } from '@/types/api/comment';

export const COMMENT_MAX = 500;
/** 이 글자 수부터 남은 글자 수를 알린다. */
const COUNT_FROM = 450;

interface CommentInputProps {
  postId: number;
  /** 답글을 달 댓글. 없으면 최상위 댓글을 쓴다. */
  replyTo: CommentResponse | null;
  onCancelReply: () => void;
  /** 서버가 받아 준 댓글. */
  onPosted?: (created: CommentResponse) => void;
  /** 입력창을 담은 화면 영역이 창 위에서 내려온 거리. 키보드와 겹침을 재는 데 쓴다. */
  keyboardOffset?: number;
}

/** 게시 단추. 쓸 수 있게 되면 떠오르고, 보내면 종이비행기가 날아간다. */
function PostButton({
  ready,
  busy,
  sent,
  onPress,
}: {
  ready: boolean;
  busy: boolean;
  sent: number;
  onPress: () => void;
}) {
  const { colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const lift = useSharedValue(ready ? 1 : 0);
  const fly = useSharedValue(0);

  useEffect(() => {
    lift.set(reduced ? (ready ? 1 : 0) : withSpring(ready ? 1 : 0, motion.spring));
  }, [lift, motion.spring, ready, reduced]);

  useEffect(() => {
    if (sent > 0 && !reduced) {
      fly.set(
        withSequence(
          withTiming(1, { duration: 140 }),
          withTiming(-1, { duration: 0 }),
          withSpring(0, motion.spring),
        ),
      );
    }
  }, [fly, motion.spring, reduced, sent]);

  const body = useAnimatedStyle(() => ({
    opacity: 0.45 + 0.55 * Math.min(1, Math.max(0, lift.get())),
    transform: [{ scale: 0.9 + 0.1 * lift.get() }],
  }));
  const plane = useAnimatedStyle(() => ({
    opacity: 1 - Math.abs(fly.get()),
    transform: [{ translateX: fly.get() * 10 }, { translateY: fly.get() * -10 }],
  }));

  return (
    <Animated.View style={body}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="게시"
        accessibilityState={{ disabled: !ready || busy, busy }}
        disabled={!ready || busy}
        hitSlop={4}
        onPress={onPress}
        style={[styles.send, { backgroundColor: ready ? colors.accent : colors.border }]}
      >
        <Animated.View style={plane}>
          <PaperPlaneTiltIcon
            size={20}
            color={ready ? colors.onAccent : colors.textMuted}
            weight="fill"
          />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

/** 화면 아래에 붙는 댓글 입력. 답글이면 위에 누구에게 쓰는지 머리가 달린다. */
export function CommentInput({
  postId,
  replyTo,
  onCancelReply,
  onPosted,
  keyboardOffset = 0,
}: CommentInputProps) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const create = useCreateComment(postId);
  const [text, setText] = useState('');
  const [sent, setSent] = useState(0);

  const trimmed = text.trim();
  const ready = trimmed.length >= 1 && trimmed.length <= COMMENT_MAX;
  const over = trimmed.length > COMMENT_MAX;

  async function post() {
    if (!ready) {
      return;
    }
    const created = await create.submit({
      body: trimmed,
      replyToCommentId: replyTo?.id ?? null,
    });
    if (created) {
      setText('');
      setSent((count) => count + 1);
      onCancelReply();
      onPosted?.(created);
    }
  }

  const springIn = reduced
    ? undefined
    : FadeInDown.springify().damping(motion.spring.damping).stiffness(motion.spring.stiffness);

  return (
    <KeyboardAvoidingView
      // Android는 Screen이 밀어 올린다.
      enabled={Platform.OS === 'ios'}
      behavior="padding"
      // KeyboardAvoidingView는 부모 기준 위치로 키보드와 겹침을 잰다. 부모가 창 위에서 내려온 만큼을
      // 넘겨 주지 않으면 그만큼 덜 올라가 입력창이 키보드에 가린다.
      keyboardVerticalOffset={keyboardOffset}
      style={{ gap: space(2), paddingTop: space(2) }}
    >
      {replyTo ? (
        <Animated.View
          key={replyTo.id}
          entering={springIn}
          exiting={reduced ? undefined : FadeOutDown.duration(140)}
          style={[
            styles.header,
            {
              backgroundColor: colors.surface,
              borderRadius: radius.input,
              paddingHorizontal: space(4),
            },
          ]}
        >
          <AppText variant="caption" tone="muted" numberOfLines={1} style={styles.shrink}>
            {`${replyTo.author?.nickname ?? ''}님에게 답글`}
          </AppText>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="답글 취소"
            hitSlop={8}
            onPress={onCancelReply}
            style={styles.cancel}
          >
            <XIcon size={16} color={colors.textMuted} weight="bold" />
          </Pressable>
        </Animated.View>
      ) : null}
      {create.error ? <ErrorBanner message={errorMessage(create.error)} /> : null}
      <View style={[styles.row, { gap: space(2) }]}>
        <TextInput
          accessibilityLabel="댓글 입력"
          placeholder={replyTo ? '답글을 남겨 보세요' : '댓글을 남겨 보세요'}
          placeholderTextColor={colors.textMuted}
          value={text}
          onChangeText={setText}
          multiline
          editable={!create.isPending}
          style={[
            styles.input,
            {
              color: colors.text,
              backgroundColor: colors.surface,
              borderColor: over ? colors.danger : colors.border,
              borderRadius: radius.input,
              paddingHorizontal: space(4),
            },
          ]}
        />
        <PostButton ready={ready} busy={create.isPending} sent={sent} onPress={() => void post()} />
      </View>
      {trimmed.length >= COUNT_FROM ? (
        <AppText variant="caption" tone={over ? 'danger' : 'muted'} style={styles.count}>
          {`${trimmed.length}/${COMMENT_MAX}`}
        </AppText>
      ) : null}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-end' },
  header: { flexDirection: 'row', alignItems: 'center', minHeight: 36 },
  shrink: { flex: 1 },
  cancel: { width: 28, height: 28, alignItems: 'center', justifyContent: 'center' },
  input: {
    flex: 1,
    minHeight: 44,
    maxHeight: 120,
    borderWidth: 1,
    fontFamily: 'Pretendard-Regular',
    fontSize: 16,
    paddingTop: 11,
    paddingBottom: 11,
  },
  send: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  count: { textAlign: 'right' },
});

