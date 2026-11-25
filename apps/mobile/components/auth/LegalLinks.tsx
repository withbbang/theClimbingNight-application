import { StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { FadeInItem } from '@/components/ui/FadeInItem';

interface LegalLinksProps {
  index: number;
  /** 로그인·가입 요청 중에는 화면을 떠나지 않게 한다. */
  disabled: boolean;
}

export function LegalLinks({ index, disabled }: LegalLinksProps) {
  const router = useRouter();
  return (
    <FadeInItem index={index} style={styles.box}>
      <AppText variant="caption" tone="muted" style={styles.text}>
        계속하면 이용약관과 개인정보처리방침에 동의하게 돼요.
      </AppText>
      <View style={styles.links}>
        <Button
          label="이용약관"
          variant="text"
          disabled={disabled}
          onPress={() => router.push('/legal/terms')}
        />
        <Button
          label="개인정보처리방침"
          variant="text"
          disabled={disabled}
          onPress={() => router.push('/legal/privacy')}
        />
      </View>
    </FadeInItem>
  );
}

const styles = StyleSheet.create({
  box: { marginTop: 16, alignItems: 'center' },
  text: { textAlign: 'center' },
  links: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center' },
});
