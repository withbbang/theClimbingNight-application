import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Image } from 'expo-image';
import { useRouter } from 'expo-router';

import { CopySimpleIcon } from 'phosphor-react-native/src/icons/CopySimple';
import { EyeSlashIcon } from 'phosphor-react-native/src/icons/EyeSlash';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';
import type { GridPostResponse } from '@/types/api/profile';

const STAGGER_MS = 35;
/** 첫 화면에 들어오는 칸(이 순서 앞)만 차례로 나온다. 스크롤로 만나는 칸은 바로 보인다. */
export const MAX_STAGGER = 12;

interface GridCellProps {
  post: GridPostResponse;
  /** 격자 안의 순서. 등장 순서를 정한다. */
  index: number;
  /**
   * 이 화면에서 이미 나타난 게시물 ID. 목록이 칸을 재활용하며 다시 그려도 다시 나타나지 않게 한다.
   * 칸이 처음 나타나면 여기에 넣는다.
   */
  shown: Set<number>;
}

function cellLabel(post: GridPostResponse) {
  if (post.thumbnailUrl === null) {
    return '숨겨진 게시물';
  }
  return post.mediaCount > 1 ? `게시물, 사진 ${post.mediaCount}장` : '게시물';
}

/** 격자 한 칸. 첫 사진의 썸네일을 정사각으로 채우고, 숨긴 글은 회색 칸에 "숨겨짐"만 둔다. */
export function GridCell({ post, index, shown }: GridCellProps) {
  const router = useRouter();
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  const press = usePressScale(0.95);
  // 첫 화면의 칸이 처음 그려질 때만 나타나는 연출을 준다. 처음 그릴 때 한 번만 정한다.
  const [enter] = useState(() => !reduced && index < MAX_STAGGER && !shown.has(post.id));
  const appear = useSharedValue(enter ? 0 : 1);
  const hidden = post.thumbnailUrl === null;

  useEffect(() => {
    shown.add(post.id);
  }, [post.id, shown]);

  // 칸이 사진을 붙이듯 살짝 작게 나타나 제자리로 커진다.
  useEffect(() => {
    if (enter) {
      appear.set(withDelay(index * STAGGER_MS, withSpring(1, motion.spring)));
    }
    // 처음 그릴 때 한 번만 움직인다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const appearStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, appear.get())),
    transform: [{ scale: 0.9 + 0.1 * appear.get() }],
  }));

  return (
    <Animated.View testID="grid-cell" style={[styles.slot, appearStyle]}>
      <Animated.View style={press.style}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={cellLabel(post)}
          onPress={() => router.push(`/posts/${post.id}`)}
          {...press.handlers}
          style={[styles.cell, { backgroundColor: hidden ? colors.holdOff : colors.surface }]}
        >
          {hidden ? (
            <View
              style={[styles.hidden, { gap: space(1) }]}
              accessibilityElementsHidden
              importantForAccessibility="no-hide-descendants"
            >
              <EyeSlashIcon size={22} color={colors.textMuted} weight="bold" />
              <AppText variant="caption" tone="muted">
                숨겨짐
              </AppText>
            </View>
          ) : (
            <Image
              source={{ uri: post.thumbnailUrl ?? undefined }}
              style={StyleSheet.absoluteFill}
              contentFit="cover"
              transition={reduced ? 0 : 180}
              recyclingKey={String(post.id)}
              accessibilityIgnoresInvertColors
            />
          )}
          {post.mediaCount > 1 ? (
            <View
              testID="multi-media"
              style={[styles.multi, { top: space(1.5), right: space(1.5) }]}
              accessibilityElementsHidden
              importantForAccessibility="no-hide-descendants"
            >
              {/* 흰 사진 위에서도 보이도록 아이콘 뒤에 옅은 어둠을 깐다. */}
              {hidden ? null : (
                <View style={[styles.scrim, { backgroundColor: colors.photoShade }]} />
              )}
              <CopySimpleIcon
                size={18}
                color={hidden ? colors.textMuted : colors.onPhoto}
                weight="fill"
              />
            </View>
          ) : null}
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  slot: { flex: 1 },
  cell: { width: '100%', aspectRatio: 1, overflow: 'hidden' },
  hidden: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  multi: {
    position: 'absolute',
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scrim: {
    ...StyleSheet.absoluteFill,
    opacity: 0.35,
    // 앱의 홀드 모양을 작게.
    borderTopLeftRadius: 11,
    borderTopRightRadius: 8,
    borderBottomRightRadius: 12,
    borderBottomLeftRadius: 9,
  },
});
