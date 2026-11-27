import { useEffect, useRef } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { HeartIcon } from 'phosphor-react-native/src/icons/Heart';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  type SharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';

const ICON = 22;
const SPARKS = 6;
const SPARK_REACH = 15;

interface SparkProps {
  index: number;
  pop: SharedValue<number>;
}

/** 켜지는 순간 하트 둘레로 번졌다 사라지는 점. */
function Spark({ index, pop }: SparkProps) {
  const { colors } = useTheme();
  const angle = (index / SPARKS) * Math.PI * 2 - Math.PI / 2;
  const style = useAnimatedStyle(() => {
    const t = pop.get();
    return {
      opacity: t === 0 || t === 1 ? 0 : 1 - t,
      transform: [
        { translateX: Math.cos(angle) * SPARK_REACH * t },
        { translateY: Math.sin(angle) * SPARK_REACH * t },
        { scale: 1 - 0.6 * t },
      ],
    };
  });
  return <Animated.View style={[styles.spark, { backgroundColor: colors.accent }, style]} />;
}

interface LikeButtonProps {
  liked: boolean;
  count: number;
  /** false를 돌려주면 값이 바뀌지 않는 누름(로그인 화면으로 보냄 등)이라 연출을 기다리지 않는다. */
  onPress: () => void | boolean;
  /** 값이 올라갈 때마다 눌림 없이도 켜짐 연출을 한 번 보인다(사진 두 번 탭). */
  celebrate?: number;
  /** 켜지 못하게 막는다. 켜진 하트는 이 값과 상관없이 끌 수 있다. */
  disabled?: boolean;
}

/**
 * 하트와 좋아요 수. 켜지면 채운 하트가 작게 시작해 튕기며 커졌다 자리잡고 점이 번진다.
 * 꺼지면 채운 하트가 사라지며 윤곽만 남는다.
 */
export function LikeButton({
  liked,
  count,
  onPress,
  celebrate = 0,
  disabled = false,
}: LikeButtonProps) {
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  const press = usePressScale(0.82);
  const fill = useSharedValue(liked ? 1 : 0);
  const bounce = useSharedValue(1);
  const pop = useSharedValue(0);
  const first = useRef(true);
  // 이 카드에서 누른 뒤 처음 바뀌는 값만 연출한다. 재활용 셀의 새 글, 서버 응답, 되돌림은 끝 상태로 맞춘다.
  // 시계에 기대지 않고 누름 한 번이 값 변경 한 번을 소비한다.
  const pendingPress = useRef(false);
  const seenCelebrate = useRef(celebrate);

  useEffect(() => {
    const celebrated = celebrate !== seenCelebrate.current;
    seenCelebrate.current = celebrate;
    if (first.current) {
      first.current = false;
      pendingPress.current = false;
      return;
    }
    if (celebrated && !liked) {
      // 두 번 탭은 캐시 갱신(liked)보다 한 렌더 먼저 온다. 꺼짐 연출 없이, 다음에 켜지는 변경을 연출하도록 남긴다.
      pendingPress.current = true;
      return;
    }
    const play = celebrated || pendingPress.current;
    pendingPress.current = false;
    if (reduced || !play) {
      fill.set(liked ? 1 : 0);
      return;
    }
    if (liked) {
      fill.set(0);
      fill.set(withSpring(1, { damping: 7, stiffness: 220 }));
      bounce.set(withSequence(withTiming(0.7, { duration: 80 }), withSpring(1, motion.spring)));
      pop.set(0);
      pop.set(withTiming(1, { duration: 520 }));
    } else {
      fill.set(withTiming(0, { duration: 140 }));
    }
  }, [bounce, celebrate, fill, liked, motion.spring, pop, reduced]);

  const filled = useAnimatedStyle(() => ({
    opacity: Math.min(1, fill.get() * 2),
    transform: [{ scale: fill.get() }],
  }));
  const outer = useAnimatedStyle(() => ({ transform: [{ scale: bounce.get() }] }));

  return (
    <Animated.View style={press.style}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={liked ? '좋아요 취소' : '좋아요'}
        accessibilityState={{ selected: liked, disabled: disabled && !liked }}
        disabled={disabled && !liked}
        hitSlop={space(2)}
        onPress={() => {
          pendingPress.current = onPress() !== false;
        }}
        {...press.handlers}
        style={[styles.row, { gap: space(1) }]}
      >
        <Animated.View style={[styles.icon, outer]}>
          <HeartIcon size={ICON} color={liked ? colors.accent : colors.text} weight="bold" />
          <Animated.View testID="like-fill" style={[styles.layer, filled]} pointerEvents="none">
            <HeartIcon size={ICON} color={colors.accent} weight="fill" />
          </Animated.View>
          <View style={styles.layer} pointerEvents="none">
            {Array.from({ length: SPARKS }, (_, index) => (
              <Spark key={index} index={index} pop={pop} />
            ))}
          </View>
        </Animated.View>
        <AppText variant="label">
          {count}
        </AppText>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
  icon: { width: ICON, height: ICON, alignItems: 'center', justifyContent: 'center' },
  layer: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  spark: { position: 'absolute', width: 4, height: 4, borderRadius: 2 },
});
