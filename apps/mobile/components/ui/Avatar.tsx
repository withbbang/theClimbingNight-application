import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { useTheme } from '@/hooks/use-theme';

const AVATAR = 40;

/** 닉네임 첫 글자를 홀드 모양 위에 얹는다. */
export function Avatar({ nickname }: { nickname: string }) {
  const { colors } = useTheme();
  return (
    <View
      style={[styles.avatar, { backgroundColor: colors.accent }]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      <AppText variant="label" tone="onAccent" style={styles.initial}>
        {nickname.slice(0, 1)}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  avatar: {
    width: AVATAR,
    height: AVATAR,
    alignItems: 'center',
    justifyContent: 'center',
    // FloatingIcon과 같은 홀드 모양.
    borderTopLeftRadius: 18,
    borderTopRightRadius: 13,
    borderBottomRightRadius: 20,
    borderBottomLeftRadius: 15,
  },
  initial: { fontFamily: 'Pretendard-ExtraBold' },
});
