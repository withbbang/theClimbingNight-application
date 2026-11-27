import { View } from 'react-native';

import Animated, { FadeInDown, useReducedMotion } from 'react-native-reanimated';

import { CommentItem } from '@/components/comment/CommentItem';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useReplies } from '@/hooks/use-comments';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';

const STAGGER_MS = 60;
/** 한 번에 이만큼만 차례를 주고, 이어 받은 답글은 바로 보인다. */
const STAGGERED = 6;

/** 펼친 답글. 위에서 아래로 차례로 내려오며 나타난다. */
export function ReplyList({ postId, parentId }: { postId: number; parentId: number }) {
  const { space, colors, motion } = useTheme();
  const reduced = useReducedMotion();
  const query = useReplies(postId, parentId, true);
  const { data, hasNextPage, isFetchingNextPage, isPending, isError } = query;

  return (
    <View
      style={{
        marginLeft: space(6),
        paddingLeft: space(3),
        gap: space(3),
        borderLeftWidth: 2,
        borderLeftColor: colors.border,
      }}
    >
      {data?.map((reply, index) => (
        <Animated.View
          key={reply.id}
          entering={
            reduced
              ? undefined
              : FadeInDown.delay(Math.min(index, STAGGERED) * STAGGER_MS)
                  .springify()
                  .damping(motion.spring.damping)
                  .stiffness(motion.spring.stiffness)
          }
        >
          <CommentItem postId={postId} comment={reply} />
        </Animated.View>
      ))}
      {isPending ? <AppText tone="muted">답글을 불러오는 중</AppText> : null}
      {isError ? (
        <View style={{ gap: space(2) }}>
          <ErrorBanner message={errorMessage(query.error)} />
          <Button label="다시 시도" variant="secondary" onPress={() => void query.refetch()} />
        </View>
      ) : null}
      {hasNextPage ? (
        <Button
          label="답글 더 보기"
          variant="secondary"
          loading={isFetchingNextPage}
          onPress={() => void query.fetchNextPage()}
        />
      ) : null}
    </View>
  );
}
