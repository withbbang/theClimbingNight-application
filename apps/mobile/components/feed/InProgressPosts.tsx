import { useEffect, useRef } from 'react';
import { StyleSheet, View } from 'react-native';

import { WarningCircleIcon } from 'phosphor-react-native/src/icons/WarningCircle';
import Animated, {
  FadeInDown,
  FadeOut,
  LinearTransition,
  useReducedMotion,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { useDeleteMyPost, hasPending, useMyInProgress, useRefreshFeed } from '@/hooks/use-my-posts';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { PostResponse } from '@/types/api/post';

function PendingRow() {
  const { colors, space } = useTheme();
  return (
    <View style={[styles.row, { gap: space(3) }]}>
      <PulseHolds color={colors.accent} />
      <View style={styles.fill}>
        <AppText variant="label" accessibilityLiveRegion="polite">
          사진 정리 중…
        </AppText>
        <AppText variant="caption" tone="muted">
          다 되면 피드에 올라가요.
        </AppText>
      </View>
    </View>
  );
}

function FailedRow({ post }: { post: PostResponse }) {
  const { colors, space } = useTheme();
  const remove = useDeleteMyPost();
  return (
    <View style={{ gap: space(3) }}>
      <View style={[styles.row, { gap: space(3) }]}>
        <WarningCircleIcon size={24} color={colors.danger} weight="bold" />
        <View style={styles.fill}>
          <AppText variant="label">올리지 못했어요</AppText>
          <AppText variant="caption" tone="muted">
            {`사진 ${post.media.length}장을 정리하지 못했어요. 지우고 다시 올려 주세요.`}
          </AppText>
        </View>
      </View>
      <ConfirmAction
        label="삭제"
        confirmLabel="삭제 확정"
        target="올리지 못한 글"
        message="이 글과 사진을 지워요."
        loading={remove.isPending}
        error={remove.error ? errorMessage(remove.error) : null}
        onConfirm={async () => (await remove.submit(post.id)) === true}
      />
    </View>
  );
}

/** 피드 맨 위에 내가 올리는 중인 글과 올리지 못한 글을 보여 준다. */
export function InProgressPosts() {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const query = useMyInProgress();
  const refreshFeed = useRefreshFeed();
  const pending = hasPending(query.data);
  const wasPending = useRef(false);

  // 정리가 끝나면 묻기를 멈추고, 새 글이 보이도록 피드를 다시 받는다.
  useEffect(() => {
    if (wasPending.current && !pending && query.isSuccess) {
      void refreshFeed();
    }
    wasPending.current = pending;
  }, [pending, query.isSuccess, refreshFeed]);

  const posts = (query.data ?? []).filter(
    (post) => post.status === 'PENDING' || post.status === 'FAILED',
  );
  if (posts.length === 0) {
    return null;
  }
  return (
    <View style={{ gap: space(2) }}>
      {posts.map((post) => (
        <Animated.View
          key={post.id}
          entering={
            reduced
              ? undefined
              : FadeInDown.springify()
                  .damping(motion.spring.damping)
                  .stiffness(motion.spring.stiffness)
          }
          exiting={reduced ? undefined : FadeOut.duration(200)}
          layout={reduced ? undefined : LinearTransition}
          style={{
            padding: space(4),
            borderRadius: radius.card,
            backgroundColor: colors.surface,
            borderWidth: post.status === 'FAILED' ? 1.5 : 0,
            borderColor: colors.danger,
          }}
        >
          {post.status === 'PENDING' ? <PendingRow /> : <FailedRow post={post} />}
        </Animated.View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
});
