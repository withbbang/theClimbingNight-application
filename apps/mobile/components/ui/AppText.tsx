import { Text, type TextProps, type TextStyle } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

// 글꼴 파일마다 굵기가 하나라 fontWeight 대신 fontFamily로 굵기를 고른다.
const VARIANTS = {
  caption: { fontFamily: 'Pretendard-Regular', fontSize: 13, lineHeight: 19 },
  body: { fontFamily: 'Pretendard-Regular', fontSize: 15, lineHeight: 23 },
  label: { fontFamily: 'Pretendard-SemiBold', fontSize: 17, lineHeight: 24 },
  title: { fontFamily: 'Pretendard-ExtraBold', fontSize: 22, lineHeight: 30, letterSpacing: -0.4 },
  display: {
    fontFamily: 'Pretendard-ExtraBold',
    fontSize: 30,
    lineHeight: 38,
    letterSpacing: -0.8,
  },
} satisfies Record<string, TextStyle>;

const TONES = {
  default: 'text',
  muted: 'textMuted',
  danger: 'danger',
  onAccent: 'onAccent',
} as const;

interface AppTextProps extends TextProps {
  variant?: keyof typeof VARIANTS;
  tone?: keyof typeof TONES;
}

export function AppText({ variant = 'body', tone = 'default', style, ...props }: AppTextProps) {
  const { colors } = useTheme();
  return <Text {...props} style={[VARIANTS[variant], { color: colors[TONES[tone]] }, style]} />;
}
