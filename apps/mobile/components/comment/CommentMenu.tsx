import { useState, type ReactNode } from 'react';
import { View } from 'react-native';

import { FlagIcon } from 'phosphor-react-native/src/icons/Flag';
import Animated, { FadeIn, FadeInDown, useReducedMotion } from 'react-native-reanimated';

import { ReportSheet } from '@/components/feed/ReportSheet';
import { Button } from '@/components/ui/Button';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { useDeleteComment } from '@/hooks/use-comments';
import { useBlockUser } from '@/hooks/use-moderation';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { CommentResponse } from '@/types/api/comment';

const STAGGER_MS = 50;

interface CommentMenuProps {
  postId: number;
  comment: CommentResponse;
  onClose: () => void;
}

/** 메뉴 항목이 위에서부터 차례로 내려앉는다. */
function Item({ index, children }: { index: number; children: ReactNode }) {
  const { motion } = useTheme();
  const reduced = useReducedMotion();
  return (
    <Animated.View
      entering={
        reduced
          ? undefined
          : FadeInDown.delay(index * STAGGER_MS)
              .springify()
              .damping(motion.spring.damping)
              .stiffness(motion.spring.stiffness)
      }
    >
      {children}
    </Animated.View>
  );
}

/** 댓글의 ⋯ 메뉴. 지울 수 있으면 삭제, 남의 댓글이면 신고·차단. */
export function CommentMenu({ postId, comment, onClose }: CommentMenuProps) {
  const { colors, space } = useTheme();
  const reduced = useReducedMotion();
  const remove = useDeleteComment(postId);
  const block = useBlockUser();
  const [reporting, setReporting] = useState(false);
  const nickname = comment.author?.nickname ?? '';

  if (reporting) {
    return (
      <Animated.View entering={reduced ? undefined : FadeIn.duration(180)}>
        <ReportSheet targetType="COMMENT" targetId={comment.id} onClose={onClose} />
      </Animated.View>
    );
  }
  return (
    <View accessibilityLabel="댓글 메뉴" style={{ gap: space(2) }}>
      {comment.deletable ? (
        <Item index={0}>
          <ConfirmAction
            label="삭제"
            confirmLabel="삭제 확정"
            message="이 댓글을 지워요. 되돌릴 수 없어요."
            loading={remove.isPending}
            error={remove.error ? errorMessage(remove.error) : null}
            onConfirm={async () => {
              const done = (await remove.submit(comment.id)) === true;
              if (done) {
                onClose();
              }
              return done;
            }}
          />
        </Item>
      ) : null}
      {comment.mine ? null : (
        <>
          <Item index={1}>
            <Button
              label="신고"
              variant="secondary"
              icon={<FlagIcon size={18} color={colors.text} weight="bold" />}
              onPress={() => setReporting(true)}
            />
          </Item>
          <Item index={2}>
            <ConfirmAction
              label={`${nickname}님 차단`}
              confirmLabel="차단 확정"
              message="서로의 글과 댓글이 보이지 않아요. 마이 탭의 차단 목록에서 풀 수 있어요."
              loading={block.isPending}
              error={block.error ? errorMessage(block.error) : null}
              onConfirm={async () => {
                const done = (await block.submit(comment.author?.id ?? 0)) === true;
                if (done) {
                  onClose();
                }
                return done;
              }}
            />
          </Item>
        </>
      )}
      <Item index={3}>
        <Button label="닫기" variant="text" onPress={onClose} />
      </Item>
    </View>
  );
}
