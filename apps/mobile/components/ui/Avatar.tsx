import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Image } from 'expo-image';

import { useReducedMotion } from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/hooks/use-theme';

const DEFAULT_SIZE = 40;

interface AvatarProps {
  nickname: string;
  /** 프로필 사진 주소. 없거나 불러오지 못하면 닉네임 첫 글자를 보여 준다. */
  imageUrl?: string | null;
  size?: number;
  /** 사진이 바뀌면 예전 사진 위로 새 사진을 겹쳐 보인다. 목록 줄처럼 재활용되는 곳에는 쓰지 않는다. */
  crossfade?: boolean;
}

/** 프로필 사진, 없으면 닉네임 첫 글자를 홀드 모양 위에 얹는다. */
export function Avatar({
  nickname,
  imageUrl,
  size = DEFAULT_SIZE,
  crossfade = false,
}: AvatarProps) {
  const { colors } = useTheme();
  const reduced = useReducedMotion();
  // 실패한 주소를 기억해 두면 주소가 바뀔 때(새 사진) 다시 시도한다.
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const showImage = !!imageUrl && imageUrl !== failedUrl;
  const scale = size / DEFAULT_SIZE;
  return (
    <View
      style={[
        styles.avatar,
        {
          width: size,
          height: size,
          backgroundColor: colors.accent,
          // FloatingIcon과 같은 홀드 모양. 크기에 비례한다.
          borderTopLeftRadius: 18 * scale,
          borderTopRightRadius: 13 * scale,
          borderBottomRightRadius: 20 * scale,
          borderBottomLeftRadius: 15 * scale,
        },
      ]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {showImage ? (
        <Image
          source={{ uri: imageUrl }}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          // 재활용된 줄이 앞사람 사진을 잠깐 보이지 않게 주소로 묶는다.
          recyclingKey={imageUrl}
          transition={crossfade && !reduced ? 240 : 0}
          onError={() => setFailedUrl(imageUrl)}
        />
      ) : (
        <AppText
          variant="label"
          tone="onAccent"
          style={[styles.initial, { fontSize: Math.round(14 * scale) }]}
        >
          {nickname.slice(0, 1)}
        </AppText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  avatar: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  initial: { fontFamily: 'Pretendard-ExtraBold' },
});
