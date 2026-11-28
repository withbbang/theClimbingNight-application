import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { CameraIcon } from 'phosphor-react-native/src/icons/Camera';
import Animated, {
  FadeIn,
  FadeInDown,
  FadeOut,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { useProfileImage, type ProfileImagePhase } from '@/hooks/use-profile-image';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';

const AVATAR_SIZE = 112;
const BADGE_SIZE = 36;

const PHASE_TEXT: Record<Exclude<ProfileImagePhase, 'idle'>, string> = {
  uploading: '올리는 중',
  applying: '적용하는 중',
  slow: '잠시 뒤 반영돼요',
  failed: '사진을 적용하지 못했어요. 다시 올려 주세요',
};

interface ProfilePhotoEditorProps {
  nickname: string;
  /** 지금 사진(320px). 없으면 기본 이미지. */
  imageUrl: string | null;
}

/** 지금 단계를 알린다. 단계가 바뀌면 앞 문구가 사라지며 새 문구가 겹쳐 나타난다. */
function PhaseLine({ phase }: { phase: ProfileImagePhase }) {
  const { colors, space } = useTheme();
  const reduced = useReducedMotion();
  if (phase === 'idle') {
    return null;
  }
  const busy = phase === 'uploading' || phase === 'applying';
  return (
    <Animated.View
      key={phase}
      entering={reduced ? undefined : FadeIn.duration(220)}
      exiting={reduced ? undefined : FadeOut.duration(160)}
      style={[styles.row, { gap: space(2) }]}
    >
      {busy ? <PulseHolds color={colors.accent} /> : null}
      <AppText
        variant="caption"
        tone={phase === 'failed' ? 'danger' : 'muted'}
        accessibilityLiveRegion="polite"
      >
        {PHASE_TEXT[phase]}
      </AppText>
    </Animated.View>
  );
}

/** 프로필 사진. 누르면 "사진 바꾸기 / 기본 이미지로"가 펼쳐진다. 올리고 적용하는 동안은 잠근다. */
export function ProfilePhotoEditor({ nickname, imageUrl }: ProfilePhotoEditorProps) {
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  const image = useProfileImage();
  const press = usePressScale(0.94);
  const [open, setOpen] = useState(false);
  const busy = image.phase === 'uploading' || image.phase === 'applying';
  // 고른 사진은 적용될 때까지 흐리게 보여 준다.
  const dim = useSharedValue(busy ? 1 : 0);

  useEffect(() => {
    const target = busy ? 1 : 0;
    dim.set(reduced ? target : withTiming(target, { duration: 240 }));
  }, [busy, dim, reduced]);

  // 펼치면 사진 아래 카메라 표시가 살짝 돈다.
  const turn = useSharedValue(open ? 1 : 0);
  useEffect(() => {
    const target = open ? 1 : 0;
    turn.set(reduced ? target : withSpring(target, motion.spring));
  }, [motion.spring, open, reduced, turn]);

  const dimStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.45 * dim.get() }));
  const badgeStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${turn.get() * -12}deg` }, { scale: 1 + 0.08 * turn.get() }],
  }));

  const choose = (action: () => Promise<void>) => {
    setOpen(false);
    void action();
  };

  return (
    <View style={{ gap: space(3) }}>
      <View style={[styles.row, { gap: space(4) }]}>
        <Animated.View style={press.style}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="프로필 사진 바꾸기"
            accessibilityState={{ expanded: open }}
            onPress={() => setOpen((value) => !value)}
            {...press.handlers}
          >
            <Animated.View style={dimStyle}>
              <Avatar
                nickname={nickname}
                imageUrl={image.preview ?? imageUrl}
                size={AVATAR_SIZE}
                crossfade
              />
            </Animated.View>
            <Animated.View
              style={[
                styles.badge,
                { backgroundColor: colors.accent, borderColor: colors.background },
                badgeStyle,
              ]}
            >
              <CameraIcon size={18} color={colors.onAccent} weight="bold" />
            </Animated.View>
          </Pressable>
        </Animated.View>
        <View style={[styles.fill, { gap: space(1) }]}>
          <AppText variant="label">프로필 사진</AppText>
          {image.phase === 'idle' ? (
            <AppText variant="caption" tone="muted">
              사진을 눌러 바꿀 수 있어요.
            </AppText>
          ) : (
            <PhaseLine phase={image.phase} />
          )}
        </View>
      </View>
      {open ? (
        <Animated.View
          entering={
            reduced
              ? undefined
              : FadeInDown.springify()
                  .damping(motion.spring.damping)
                  .stiffness(motion.spring.stiffness)
          }
          style={{ gap: space(2) }}
        >
          <Button
            label="사진 바꾸기"
            variant="secondary"
            disabled={busy}
            onPress={() => choose(image.pick)}
          />
          {imageUrl ? (
            <Button
              label="기본 이미지로"
              variant="text"
              disabled={busy}
              onPress={() => choose(image.reset)}
            />
          ) : null}
        </Animated.View>
      ) : null}
      {image.error ? <ErrorBanner message={image.error} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
  badge: {
    position: 'absolute',
    right: -4,
    bottom: -4,
    width: BADGE_SIZE,
    height: BADGE_SIZE,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'center',
    // 앱의 홀드 모양.
    borderTopLeftRadius: 17,
    borderTopRightRadius: 12,
    borderBottomRightRadius: 18,
    borderBottomLeftRadius: 14,
  },
});
