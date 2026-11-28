import { useState, type ReactNode } from 'react';
import { View } from 'react-native';

import { useQueryClient } from '@tanstack/react-query';
import { FlagIcon } from 'phosphor-react-native/src/icons/Flag';
import Animated, { FadeIn, FadeInDown, useReducedMotion } from 'react-native-reanimated';

import { ReportSheet } from '@/components/feed/ReportSheet';
import { Button } from '@/components/ui/Button';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { useBlockUser } from '@/hooks/use-moderation';
import { useTheme } from '@/hooks/use-theme';
import { profileKey } from '@/hooks/use-user-profile';
import { errorMessage } from '@/lib/error-message';

const STAGGER_MS = 50;

interface ProfileMenuProps {
  userId: number;
  nickname: string;
  onClose: () => void;
  /** 차단해서 이 프로필을 더 볼 수 없게 됐을 때. */
  onGone: () => void;
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

/** 남의 프로필 ⋯ 메뉴. 신고와 차단. */
export function ProfileMenu({ userId, nickname, onClose, onGone }: ProfileMenuProps) {
  const { colors, space } = useTheme();
  const reduced = useReducedMotion();
  const client = useQueryClient();
  const block = useBlockUser();
  const [reporting, setReporting] = useState(false);

  if (reporting) {
    return (
      <Animated.View entering={reduced ? undefined : FadeIn.duration(180)}>
        <ReportSheet targetType="USER" targetId={userId} onClose={onClose} />
      </Animated.View>
    );
  }
  return (
    <View accessibilityLabel="프로필 메뉴" style={{ gap: space(2) }}>
      <Item index={0}>
        <Button
          label="신고하기"
          variant="secondary"
          icon={<FlagIcon size={18} color={colors.text} weight="bold" />}
          onPress={() => setReporting(true)}
        />
      </Item>
      <Item index={1}>
        <ConfirmAction
          label="차단하기"
          confirmLabel="차단 확정"
          message={`${nickname}님과 서로의 글이 보이지 않아요. 마이 탭의 차단 목록에서 풀 수 있어요.`}
          loading={block.isPending}
          error={block.error ? errorMessage(block.error) : null}
          onConfirm={async () => {
            const done = (await block.submit(userId)) === true;
            if (done) {
              // 차단한 사람의 프로필은 이제 404다. 받아 둔 프로필을 다시 보여 주지 않게 한다.
              void client.invalidateQueries({ queryKey: profileKey(userId) });
              onGone();
            }
            return done;
          }}
        />
      </Item>
      <Item index={2}>
        <Button label="닫기" variant="text" onPress={onClose} />
      </Item>
    </View>
  );
}
