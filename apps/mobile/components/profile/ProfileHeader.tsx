import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { DotsThreeIcon } from 'phosphor-react-native/src/icons/DotsThree';
import { PencilSimpleIcon } from 'phosphor-react-native/src/icons/PencilSimple';
import Animated, {
  FadeInDown,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { ProfileMenu } from '@/components/profile/ProfileMenu';
import { AppText } from '@/components/ui/AppText';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';
import type { ProfileResponse } from '@/types/api/profile';

const AVATAR_SIZE = 88;

interface ProfileHeaderProps {
  profile: ProfileResponse;
  /** 차단해서 이 프로필을 더 볼 수 없게 됐을 때. */
  onGone: () => void;
}

/** 프로필 사진이 홀드를 잡듯 작게 붙었다가 제 크기로 튀어 오른다. */
function HeaderAvatar({ profile }: { profile: ProfileResponse }) {
  const { motion } = useTheme();
  const reduced = useReducedMotion();
  const grab = useSharedValue(reduced ? 1 : 0);

  useEffect(() => {
    if (!reduced) {
      grab.set(
        withSpring(1, { damping: motion.spring.damping - 6, stiffness: motion.spring.stiffness }),
      );
    }
  }, [grab, motion.spring, reduced]);

  const style = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, grab.get())),
    transform: [{ scale: 0.6 + 0.4 * grab.get() }, { rotate: `${(1 - grab.get()) * -8}deg` }],
  }));

  return (
    <Animated.View style={style}>
      <Avatar
        nickname={profile.nickname}
        imageUrl={profile.profileImage?.largeUrl}
        size={AVATAR_SIZE}
        crossfade
      />
    </Animated.View>
  );
}

/** ⋯ 버튼. 펼치면 점이 세로로 돈다. */
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
        accessibilityLabel={`${nickname} 프로필 메뉴`}
        accessibilityState={{ expanded: open }}
        hitSlop={8}
        onPress={onToggle}
        {...press.handlers}
        style={styles.menuButton}
      >
        <Animated.View style={spin}>
          <DotsThreeIcon size={26} color={colors.text} weight="bold" />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

/** 프로필 위쪽: 사진, 게시물 수, 닉네임, 자기소개. 내 프로필이면 편집, 남의 프로필이면 ⋯ 메뉴. */
export function ProfileHeader({ profile, onGone }: ProfileHeaderProps) {
  const router = useRouter();
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <View style={{ gap: space(4) }}>
      <View style={[styles.top, { gap: space(5) }]}>
        <HeaderAvatar profile={profile} />
        <FadeInItem index={1} style={styles.count}>
          <AppText variant="display" accessibilityLabel={`게시물 ${profile.postCount}개`}>
            {String(profile.postCount)}
          </AppText>
          <AppText variant="caption" tone="muted" accessibilityElementsHidden>
            게시물
          </AppText>
        </FadeInItem>
        {profile.mine ? null : (
          <MenuButton
            nickname={profile.nickname}
            open={menuOpen}
            onToggle={() => setMenuOpen((open) => !open)}
          />
        )}
      </View>
      <FadeInItem index={2} style={{ gap: space(1) }}>
        <AppText variant="title" accessibilityRole="header" numberOfLines={1}>
          {profile.nickname}
        </AppText>
        {profile.bio ? (
          <AppText testID="profile-bio" selectable>
            {profile.bio}
          </AppText>
        ) : null}
      </FadeInItem>
      {profile.mine ? (
        <FadeInItem index={3}>
          <Button
            label="프로필 편집"
            variant="secondary"
            icon={<PencilSimpleIcon size={18} color={colors.text} weight="bold" />}
            onPress={() => router.push('/settings/profile')}
          />
        </FadeInItem>
      ) : null}
      {menuOpen ? (
        <Animated.View
          entering={
            reduced
              ? undefined
              : FadeInDown.springify()
                  .damping(motion.spring.damping)
                  .stiffness(motion.spring.stiffness)
          }
        >
          <ProfileMenu
            userId={profile.id}
            nickname={profile.nickname}
            onClose={() => setMenuOpen(false)}
            onGone={onGone}
          />
        </Animated.View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  top: { flexDirection: 'row', alignItems: 'center' },
  count: { flex: 1 },
  menuButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
});
