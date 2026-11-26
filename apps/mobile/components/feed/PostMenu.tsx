import { useState, type ReactNode } from 'react';
import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { FlagIcon } from 'phosphor-react-native/src/icons/Flag';
import { PencilSimpleIcon } from 'phosphor-react-native/src/icons/PencilSimple';
import Animated, { FadeIn, FadeInDown, useReducedMotion } from 'react-native-reanimated';

import { ReportSheet } from '@/components/feed/ReportSheet';
import { Button } from '@/components/ui/Button';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { useHidePost, useBlockUser } from '@/hooks/use-moderation';
import { useDeleteMyPost } from '@/hooks/use-my-posts';
import { useIsAdmin } from '@/hooks/use-role';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { PostResponse } from '@/types/api/post';

const STAGGER_MS = 50;
const HIDDEN = ['HIDDEN', 'HIDDEN_PENDING'];

interface PostMenuProps {
  post: PostResponse;
  onClose: () => void;
  /** 지우거나 차단하거나 숨겨서 이 글을 더 볼 수 없게 됐을 때. */
  onGone?: () => void;
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

function MyPostActions({
  post,
  onClose,
  onGone,
}: {
  post: PostResponse;
  onClose: () => void;
  onGone?: () => void;
}) {
  const router = useRouter();
  const { colors } = useTheme();
  const remove = useDeleteMyPost();
  const hidden = post.status !== null && HIDDEN.includes(post.status);
  return (
    <>
      {/* 숨겨진 글은 API가 고치지 못하게 막는다. */}
      {hidden ? null : (
        <Item index={0}>
          <Button
            label="수정"
            variant="secondary"
            icon={<PencilSimpleIcon size={18} color={colors.text} weight="bold" />}
            onPress={() => {
              onClose();
              router.push(`/posts/${post.id}/edit`);
            }}
          />
        </Item>
      )}
      <Item index={1}>
        <ConfirmAction
          label="삭제"
          confirmLabel="삭제 확정"
          message="이 글과 사진을 지워요. 되돌릴 수 없어요."
          loading={remove.isPending}
          error={remove.error ? errorMessage(remove.error) : null}
          onConfirm={async () => {
            const done = (await remove.submit(post.id)) === true;
            if (done) {
              onGone?.();
            }
            return done;
          }}
        />
      </Item>
    </>
  );
}

function OthersPostActions({
  post,
  onReport,
  onGone,
}: {
  post: PostResponse;
  onReport: () => void;
  onGone?: () => void;
}) {
  const { colors } = useTheme();
  const admin = useIsAdmin();
  const block = useBlockUser();
  const hide = useHidePost();
  const { nickname } = post.author;
  return (
    <>
      <Item index={0}>
        <Button
          label="신고"
          variant="secondary"
          icon={<FlagIcon size={18} color={colors.text} weight="bold" />}
          onPress={onReport}
        />
      </Item>
      <Item index={1}>
        <ConfirmAction
          label={`${nickname}님 차단`}
          confirmLabel="차단 확정"
          message="서로의 글이 보이지 않아요. 마이 탭의 차단 목록에서 풀 수 있어요."
          loading={block.isPending}
          error={block.error ? errorMessage(block.error) : null}
          onConfirm={async () => {
            const done = (await block.submit(post.author.id)) === true;
            if (done) {
              onGone?.();
            }
            return done;
          }}
        />
      </Item>
      {admin ? (
        <Item index={2}>
          <ConfirmAction
            label="숨기기"
            confirmLabel="숨기기 확정"
            message="피드에서 바로 내리고 사진을 격리해요. 작성자에게는 숨겨진 글로 보여요."
            loading={hide.isPending}
            error={hide.error ? errorMessage(hide.error) : null}
            onConfirm={async () => {
              const done = (await hide.submit(post.id)) === true;
              if (done) {
                onGone?.();
              }
              return done;
            }}
          />
        </Item>
      ) : null}
    </>
  );
}

/** 게시물의 ⋯ 메뉴. 내 글이면 수정·삭제, 남의 글이면 신고·차단(운영진은 숨기기까지). */
export function PostMenu({ post, onClose, onGone }: PostMenuProps) {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  const [reporting, setReporting] = useState(false);

  if (reporting) {
    return (
      <Animated.View entering={reduced ? undefined : FadeIn.duration(180)}>
        <ReportSheet targetType="POST" targetId={post.id} onClose={onClose} />
      </Animated.View>
    );
  }
  return (
    <View accessibilityLabel="게시물 메뉴" style={{ gap: space(2) }}>
      {post.mine ? (
        <MyPostActions post={post} onClose={onClose} onGone={onGone} />
      ) : (
        <OthersPostActions post={post} onReport={() => setReporting(true)} onGone={onGone} />
      )}
      <Item index={3}>
        <Button label="닫기" variant="text" onPress={onClose} />
      </Item>
    </View>
  );
}
