import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { ImagesSquareIcon } from 'phosphor-react-native/src/icons/ImagesSquare';

import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { useTheme } from '@/hooks/use-theme';

/** 비로그인 미리보기의 끝. 더 불러오지 않고 로그인으로 안내한다. */
export function LoginToSeeMore() {
  const router = useRouter();
  const { colors, radius, space } = useTheme();
  return (
    <FadeInItem index={1}>
      <View
        style={{
          backgroundColor: colors.surface,
          borderRadius: radius.card,
          padding: space(5),
          marginBottom: space(6),
        }}
      >
        <Notice
          illustration={<FloatingIcon icon={ImagesSquareIcon} />}
          title="더 많은 사진은 로그인하고 볼 수 있어요"
          description="로그인 없이는 최근 사진 몇 장만 보여요."
          actionLabel="로그인하기"
          onAction={() => router.push('/login')}
        />
      </View>
    </FadeInItem>
  );
}
