import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Image } from 'expo-image';

import {
  GestureDetector,
  useExclusiveGestures,
  useTapGesture,
} from 'react-native-gesture-handler';
import Animated, {
  interpolate,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';

import { HeartBurst } from '@/components/feed/HeartBurst';
import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/hooks/use-theme';
import type { PostMediaResponse } from '@/types/api/post';

/** 세로 1:1.25부터 가로 1.91:1까지. 벗어나면 잘라서 보여 준다. */
const MIN_RATIO = 0.8;
const MAX_RATIO = 1.91;
const DOT = 6;

export function mediaRatio(first: PostMediaResponse | undefined): number {
  if (!first || first.width <= 0 || first.height <= 0) {
    return 1;
  }
  return Math.min(MAX_RATIO, Math.max(MIN_RATIO, first.width / first.height));
}

interface SlideProps {
  item: PostMediaResponse;
  index: number;
  count: number;
  width: number;
  offset: SharedValue<number>;
  onPress?: () => void;
  onDoubleTap?: () => void;
}

/** 옆 사진으로 넘기는 동안 떠나는 사진은 조금 작아지고 흐려진다. */
function Slide({ item, index, count, width, offset, onPress, onDoubleTap }: SlideProps) {
  const { colors, space } = useTheme();
  const reduced = useReducedMotion();
  const label = `사진 ${count}장 중 ${index + 1}번째`;

  const animated = useAnimatedStyle(() => {
    if (reduced) {
      return { opacity: 1, transform: [{ scale: 1 }] };
    }
    const distance = Math.min(1, Math.abs(offset.get() / width - index));
    return {
      opacity: interpolate(distance, [0, 1], [1, 0.55]),
      transform: [{ scale: interpolate(distance, [0, 1], [1, 0.92]) }],
    };
  });

  const content = item.url ? (
    <Image
      source={item.url}
      placeholder={item.thumbnailUrl}
      placeholderContentFit="cover"
      contentFit="cover"
      transition={reduced ? null : { duration: 240, effect: 'cross-dissolve' }}
      recyclingKey={item.url}
      accessibilityLabel={label}
      style={styles.fill}
    />
  ) : (
    <View
      accessible
      accessibilityLabel={`${label}, 정리 중`}
      style={[styles.fill, styles.pending, { backgroundColor: colors.holdOff, padding: space(4) }]}
    >
      <AppText variant="caption" tone="muted">
        사진을 정리하고 있어요
      </AppText>
    </View>
  );

  // 두 번 탭을 받는 사진은 제스처로 한 번 탭과 가른다. 두 번째 탭이 오지 않아야 한 번 탭이 된다.
  // 로그인 여부로 트리 모양이 바뀌면 목록이 다시 마운트되므로 감지기는 항상 두고 enabled로 끈다.
  const doubleTap = useTapGesture({
    enabled: Boolean(onDoubleTap),
    numberOfTaps: 2,
    runOnJS: true,
    testID: 'photo-double-tap',
    onActivate: () => onDoubleTap?.(),
  });
  const singleTap = useTapGesture({
    enabled: Boolean(onPress),
    runOnJS: true,
    testID: 'photo-single-tap',
    onActivate: () => onPress?.(),
  });
  const gesture = useExclusiveGestures(doubleTap, singleTap);

  return (
    <Animated.View style={[{ width }, styles.fill, animated]}>
      <GestureDetector gesture={gesture}>
        <View
          accessible={Boolean(onPress)}
          accessibilityRole={onPress ? 'imagebutton' : undefined}
          accessibilityLabel={onPress ? `${label}, 게시물 열기` : undefined}
          onAccessibilityTap={onPress}
          style={styles.fill}
        >
          {content}
        </View>
      </GestureDetector>
    </Animated.View>
  );
}

interface DotProps {
  index: number;
  width: number;
  offset: SharedValue<number>;
}

/** 보고 있는 사진의 점이 늘어난다. 손가락을 따라 움직이므로 자동 애니메이션이 아니다. */
function Dot({ index, width, offset }: DotProps) {
  const { colors } = useTheme();
  const animated = useAnimatedStyle(() => {
    const near = width > 0 ? 1 - Math.min(1, Math.abs(offset.get() / width - index)) : 0;
    return {
      opacity: 0.3 + 0.7 * near,
      transform: [{ scaleX: 1 + 1.6 * near }],
    };
  });
  return <Animated.View style={[styles.dot, { backgroundColor: colors.text }, animated]} />;
}

interface MediaCarouselProps {
  media: PostMediaResponse[];
  /** 주면 사진을 눌러 상세로 간다. */
  onPress?: () => void;
  /** 주면 사진을 두 번 탭할 때 부르고 큰 하트를 터뜨린다. 한 번 탭(onPress)과 겹치지 않는다. */
  onDoubleTap?: () => void;
}

export function MediaCarousel({ media, onPress, onDoubleTap }: MediaCarouselProps) {
  const { colors, space } = useTheme();
  const [width, setWidth] = useState(0);
  const [current, setCurrent] = useState(0);
  const [burst, setBurst] = useState(0);
  const offset = useSharedValue(0);
  const onScroll = useAnimatedScrollHandler((event) => {
    offset.set(event.contentOffset.x);
  });
  const ratio = mediaRatio(media[0]);

  return (
    <View>
      <View
        testID="media-carousel"
        onLayout={(event) => setWidth(event.nativeEvent.layout.width)}
        style={{ aspectRatio: ratio, backgroundColor: colors.holdOff }}
      >
        {width > 0 ? (
          <Animated.FlatList
            horizontal
            pagingEnabled
            data={media}
            keyExtractor={(item) => String(item.id)}
            showsHorizontalScrollIndicator={false}
            onScroll={onScroll}
            scrollEventThrottle={16}
            onMomentumScrollEnd={(event) =>
              setCurrent(Math.round(event.nativeEvent.contentOffset.x / width))
            }
            getItemLayout={(_, index) => ({ length: width, offset: width * index, index })}
            renderItem={({ item, index }) => (
              <Slide
                item={item}
                index={index}
                count={media.length}
                width={width}
                offset={offset}
                onPress={onPress}
                onDoubleTap={
                  onDoubleTap
                    ? () => {
                        setBurst((n) => n + 1);
                        onDoubleTap();
                      }
                    : undefined
                }
              />
            )}
          />
        ) : null}
        <HeartBurst trigger={burst} accent={colors.accent} />
      </View>
      {media.length > 1 ? (
        <View
          accessible
          accessibilityLabel={`${media.length}장 중 ${current + 1}번째 사진`}
          style={[styles.dots, { gap: space(3), paddingTop: space(3) }]}
        >
          {media.map((item, index) => (
            <Dot key={item.id} index={index} width={width} offset={offset} />
          ))}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  pending: { justifyContent: 'flex-end' },
  dots: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
  dot: { width: DOT, height: DOT, borderRadius: DOT / 2 },
});
