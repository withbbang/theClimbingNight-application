import { StyleSheet, View } from 'react-native';

import { ProhibitIcon } from 'phosphor-react-native/src/icons/Prohibit';
import Animated, {
  FadeInDown,
  FadeOutLeft,
  LinearTransition,
  useReducedMotion,
} from 'react-native-reanimated';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { Avatar } from '@/components/ui/Avatar';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { useBlocks, useUnblockUser } from '@/hooks/use-moderation';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import { formatDay, toDateKey } from '@/lib/kst';
import type { BlockedUserResponse } from '@/types/api/moderation';

const STAGGER_MS = 60;
const MAX_STAGGER = 8;

function BlockedRow({
  user,
  index,
  sending,
  locked,
  onUnblock,
}: {
  user: BlockedUserResponse;
  index: number;
  sending: boolean;
  locked: boolean;
  onUnblock: () => void;
}) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const name = user.nickname ?? '탈퇴한 회원';
  return (
    <Animated.View
      entering={
        reduced
          ? undefined
          : FadeInDown.delay(Math.min(index, MAX_STAGGER) * STAGGER_MS)
              .springify()
              .damping(motion.spring.damping)
              .stiffness(motion.spring.stiffness)
      }
      exiting={reduced ? undefined : FadeOutLeft.duration(200)}
      layout={reduced ? undefined : LinearTransition.springify().damping(motion.spring.damping)}
      style={[
        styles.row,
        {
          gap: space(3),
          padding: space(4),
          borderRadius: radius.card,
          backgroundColor: colors.surface,
        },
      ]}
    >
      <Avatar nickname={name} imageUrl={user.profileImageUrl} />
      <View style={styles.fill}>
        <AppText variant="label" numberOfLines={1}>
          {name}
        </AppText>
        <AppText variant="caption" tone="muted">
          {`${formatDay(toDateKey(user.blockedAt))}에 차단`}
        </AppText>
      </View>
      <Button
        label="차단 해제"
        accessibilityLabel={`${name} 차단 해제`}
        variant="secondary"
        loading={sending}
        disabled={locked}
        onPress={onUnblock}
      />
    </Animated.View>
  );
}

function Blocks() {
  const { space } = useTheme();
  const query = useBlocks();
  const unblock = useUnblockUser();
  return (
    <QueryState query={query}>
      {(users) =>
        users.length === 0 ? (
          <Notice
            illustration={<FloatingIcon icon={ProhibitIcon} />}
            title="차단한 사람이 없어요"
            description="게시물의 ⋯ 메뉴에서 차단하면 서로의 글이 보이지 않아요."
          />
        ) : (
          <View style={{ gap: space(3) }}>
            <AppText tone="muted">풀면 서로의 글이 다시 보여요.</AppText>
            {unblock.error ? <ErrorBanner message={errorMessage(unblock.error)} /> : null}
            {users.map((user, i) => (
              <BlockedRow
                key={user.userId}
                user={user}
                index={i}
                sending={unblock.isPending && unblock.variables === user.userId}
                locked={unblock.isPending}
                onUnblock={() => void unblock.submit(user.userId)}
              />
            ))}
          </View>
        )
      }
    </QueryState>
  );
}

export default function BlocksScreen() {
  const { space } = useTheme();
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        <View style={{ gap: space(4) }}>
          <FadeInItem index={0}>
            <AppText variant="display" accessibilityRole="header">
              차단 목록
            </AppText>
          </FadeInItem>
          <Blocks />
        </View>
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
});
