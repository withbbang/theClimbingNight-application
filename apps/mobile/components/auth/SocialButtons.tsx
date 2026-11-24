import { Image, View, type ImageSourcePropType } from 'react-native';

import {
  AppleAuthenticationButton,
  AppleAuthenticationButtonStyle,
  AppleAuthenticationButtonType,
} from 'expo-apple-authentication';

import { Button } from '@/components/ui/Button';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { useTheme } from '@/hooks/use-theme';
import type { SocialAdapter } from '@/lib/social/types';
import type { SocialProvider } from '@/types/api/auth';

import googleSymbol from '@/assets/social/google.png';
import kakaoSymbol from '@/assets/social/kakao.png';
import naverSymbol from '@/assets/social/naver.png';

interface Brand {
  label: string;
  symbol: ImageSourcePropType;
  size: { width: number; height: number };
  colors: { background: string; text: string; border?: string };
}

// 각 사 로그인 버튼 지침이 정한 색이라 테마를 따르지 않는다.
function brandOf(provider: Exclude<SocialProvider, 'APPLE'>, dark: boolean): Brand {
  switch (provider) {
    case 'KAKAO':
      return {
        label: '카카오 로그인',
        symbol: kakaoSymbol,
        size: { width: 23, height: 21 },
        colors: { background: '#FEE500', text: '#191919' },
      };
    case 'NAVER':
      return {
        label: '네이버 로그인',
        symbol: naverSymbol,
        size: { width: 22, height: 22 },
        colors: { background: '#03A94D', text: '#FFFFFF' },
      };
    case 'GOOGLE':
      return {
        label: 'Google로 로그인',
        symbol: googleSymbol,
        size: { width: 18, height: 18 },
        colors: dark
          ? { background: '#131314', text: '#E3E3E3', border: '#8E918F' }
          : { background: '#FFFFFF', text: '#1F1F1F', border: '#747775' },
      };
  }
}

interface SocialButtonsProps {
  adapters: SocialAdapter[];
  pending: SocialProvider | null;
  onPress: (adapter: SocialAdapter) => void;
}

export function SocialButtons({ adapters, pending, onPress }: SocialButtonsProps) {
  const { dark, space } = useTheme();
  return (
    <View style={{ gap: space(3) }}>
      {adapters.map((adapter, index) => {
        if (adapter.provider === 'APPLE') {
          return (
            <FadeInItem
              key={adapter.provider}
              index={index + 3}
              // Apple 버튼에는 비활성 속성이 없어 감싼 쪽에서 막는다.
              pointerEvents={pending ? 'none' : 'auto'}
            >
              <AppleAuthenticationButton
                buttonType={AppleAuthenticationButtonType.SIGN_IN}
                buttonStyle={
                  dark ? AppleAuthenticationButtonStyle.WHITE : AppleAuthenticationButtonStyle.BLACK
                }
                cornerRadius={27}
                style={{ height: 54 }}
                onPress={() => onPress(adapter)}
              />
            </FadeInItem>
          );
        }
        const brand = brandOf(adapter.provider, dark);
        return (
          <FadeInItem key={adapter.provider} index={index + 3}>
            <Button
              label={brand.label}
              colors={brand.colors}
              icon={<Image source={brand.symbol} style={brand.size} accessible={false} />}
              loading={pending === adapter.provider}
              disabled={pending !== null}
              onPress={() => onPress(adapter)}
            />
          </FadeInItem>
        );
      })}
    </View>
  );
}
