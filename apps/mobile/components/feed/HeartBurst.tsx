import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';

import { HeartIcon } from 'phosphor-react-native/src/icons/Heart';
import Animated, {
  Easing,
  interpolate,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';

const HEART = 96;
const PARTICLES = 8;
const REACH = 86;
const DURATION = 760;
/** 큰 하트의 색은 사진 위에서 보여야 하므로 테마와 상관없이 흰 하트에 강조색을 얹는다. */
const WHITE = '#FFFFFF';

interface ParticleProps {
  index: number;
  progress: SharedValue<number>;
  color: string;
}

function Particle({ index, progress, color }: ParticleProps) {
  const angle = (index / PARTICLES) * Math.PI * 2 + Math.PI / PARTICLES;
  const style = useAnimatedStyle(() => {
    const t = progress.get();
    const reach = REACH * interpolate(t, [0, 1], [0.45, 1], 'clamp');
    return {
      opacity: interpolate(t, [0, 0.15, 1], [0, 1, 0], 'clamp'),
      transform: [
        { translateX: Math.cos(angle) * reach },
        { translateY: Math.sin(angle) * reach },
        { scale: interpolate(t, [0, 0.3, 1], [0.2, 1, 0.3], 'clamp') },
      ],
    };
  });
  return <Animated.View style={[styles.particle, { backgroundColor: color }, style]} />;
}

interface HeartBurstProps {
  /** 0이면 아무것도 보이지 않는다. 값이 바뀔 때마다 한 번 터진다. */
  trigger: number;
  accent: string;
}

/** 사진 위에 큰 하트가 튕기며 나타났다 올라가며 사라지고, 둘레로 점이 퍼진다. 터치는 통과시킨다. */
export function HeartBurst({ trigger, accent }: HeartBurstProps) {
  const reduced = useReducedMotion();
  const progress = useSharedValue(1);

  useEffect(() => {
    if (trigger === 0 || reduced) {
      return;
    }
    progress.set(0);
    progress.set(withTiming(1, { duration: DURATION, easing: Easing.out(Easing.cubic) }));
  }, [progress, reduced, trigger]);

  const heart = useAnimatedStyle(() => {
    const t = progress.get();
    return {
      opacity: t >= 1 ? 0 : interpolate(t, [0, 0.12, 0.65, 1], [0, 1, 1, 0], 'clamp'),
      transform: [
        // 작게 시작해 1.25배까지 튀었다 1배로 자리잡고, 끝에서 살짝 올라간다.
        { scale: interpolate(t, [0, 0.22, 0.4, 1], [0.3, 1.25, 1, 1.05], 'clamp') },
        { translateY: interpolate(t, [0, 0.6, 1], [0, 0, -24], 'clamp') },
      ],
    };
  });

  return (
    <View
      testID="heart-burst"
      pointerEvents="none"
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={styles.layer}
    >
      {Array.from({ length: PARTICLES }, (_, index) => (
        <Particle key={index} index={index} progress={progress} color={accent} />
      ))}
      <Animated.View style={heart}>
        <HeartIcon size={HEART} color={WHITE} weight="fill" />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  layer: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center' },
  particle: { position: 'absolute', width: 10, height: 10, borderRadius: 5 },
});
