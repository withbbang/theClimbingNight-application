import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';

import { PlusIcon } from 'phosphor-react-native/src/icons/Plus';
import Animated, {
  LinearTransition,
  useReducedMotion,
  ZoomIn,
  ZoomOut,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';
import type { PreparedPhoto } from '@/lib/upload-photos';

const TILE = 104;

interface TileProps {
  photo: PreparedPhoto;
  index: number;
  locked: boolean;
  onRemove: () => void;
}

/** 고른 사진 하나. 길게 누르면 빠지며 줄어들고, 뒤의 사진이 자리를 메운다. */
function Tile({ photo, index, locked, onRemove }: TileProps) {
  const { colors, motion, radius } = useTheme();
  const reduced = useReducedMotion();
  const press = usePressScale(0.92);

  function remove() {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onRemove();
  }

  return (
    <Animated.View
      entering={
        reduced
          ? undefined
          : ZoomIn.springify()
              .damping(motion.spring.damping)
              .stiffness(motion.spring.stiffness)
              .delay(index * 50)
      }
      exiting={reduced ? undefined : ZoomOut.duration(160)}
      layout={
        reduced
          ? undefined
          : LinearTransition.springify()
              .damping(motion.spring.damping)
              .stiffness(motion.spring.stiffness)
      }
    >
      <Animated.View style={press.style}>
        <Pressable
          accessibilityRole="image"
          accessibilityLabel={`${index + 1}번째 사진`}
          accessibilityHint={locked ? undefined : '길게 누르면 뺍니다.'}
          accessibilityActions={locked ? undefined : [{ name: 'remove', label: '빼기' }]}
          onAccessibilityAction={(event) => {
            if (event.nativeEvent.actionName === 'remove') {
              remove();
            }
          }}
          disabled={locked}
          delayLongPress={350}
          onLongPress={remove}
          {...press.handlers}
          style={[styles.tile, { borderRadius: radius.input, backgroundColor: colors.holdOff }]}
        >
          <Image
            source={{ uri: photo.uri }}
            style={StyleSheet.absoluteFill}
            contentFit="cover"
            transition={reduced ? null : 180}
          />
          {/* 첫 사진의 비율이 피드 카드의 비율이 되므로 순서를 보여 준다. */}
          <View
            style={[
              styles.badge,
              { backgroundColor: index === 0 ? colors.accent : colors.surface },
            ]}
          >
            <AppText
              variant="caption"
              tone={index === 0 ? 'onAccent' : 'default'}
              style={styles.badgeText}
            >
              {index + 1}
            </AppText>
          </View>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

interface PhotoPickerStripProps {
  photos: PreparedPhoto[];
  max: number;
  /** 고른 사진을 줄이는 중. */
  preparing: boolean;
  /** 올리는 동안에는 고르거나 뺄 수 없다. */
  locked: boolean;
  onPick: () => void;
  onRemove: (index: number) => void;
}

/** 고른 사진 미리보기 줄. 끝의 칸을 눌러 더 고른다. */
export function PhotoPickerStrip({
  photos,
  max,
  preparing,
  locked,
  onPick,
  onRemove,
}: PhotoPickerStripProps) {
  const { colors, radius, space } = useTheme();
  const press = usePressScale(0.94);
  const empty = photos.length === 0;
  const canPick = photos.length < max && !locked;

  return (
    <View style={{ gap: space(2) }}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: space(2) }}
      >
        {photos.map((photo, index) => (
          <Tile
            key={photo.uri}
            photo={photo}
            index={index}
            locked={locked}
            onRemove={() => onRemove(index)}
          />
        ))}
        {canPick || preparing ? (
          <Animated.View style={press.style}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="사진 고르기"
              accessibilityState={{ disabled: preparing, busy: preparing }}
              disabled={preparing}
              onPress={onPick}
              {...press.handlers}
              style={[
                styles.tile,
                styles.add,
                empty ? styles.addWide : null,
                { borderRadius: radius.input, borderColor: colors.border, gap: space(1) },
              ]}
            >
              {preparing ? (
                <PulseHolds color={colors.text} />
              ) : (
                <>
                  <PlusIcon size={26} color={colors.text} weight="bold" />
                  <AppText variant="caption">{empty ? '사진 고르기' : '더 고르기'}</AppText>
                </>
              )}
            </Pressable>
          </Animated.View>
        ) : null}
      </ScrollView>
      <AppText variant="caption" tone="muted">
        {empty
          ? `${max}장까지 고를 수 있어요.`
          : `${photos.length}/${max}장. 길게 누르면 사진을 뺄 수 있어요.`}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  tile: { width: TILE, height: TILE, overflow: 'hidden' },
  add: { alignItems: 'center', justifyContent: 'center', borderWidth: 1.5, borderStyle: 'dashed' },
  addWide: { width: TILE * 2 + 8 },
  badge: {
    position: 'absolute',
    top: 6,
    left: 6,
    minWidth: 22,
    height: 22,
    paddingHorizontal: 6,
    alignItems: 'center',
    justifyContent: 'center',
    // 홀드 모양.
    borderTopLeftRadius: 11,
    borderTopRightRadius: 8,
    borderBottomRightRadius: 12,
    borderBottomLeftRadius: 9,
  },
  badgeText: { fontFamily: 'Pretendard-ExtraBold' },
});
