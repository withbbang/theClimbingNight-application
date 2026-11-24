import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Redirect, useRouter } from 'expo-router';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { Screen } from '@/components/ui/Screen';
import { TextField } from '@/components/ui/TextField';
import { useSignup } from '@/hooks/use-signup';
import { useTheme } from '@/hooks/use-theme';
import { pendingSignup } from '@/lib/pending-signup';

export default function SignupScreen() {
  const router = useRouter();
  const { space } = useTheme();
  // 가입을 마치고 화면이 닫히는 동안 로그인으로 튕기지 않게 처음 값을 붙잡아 둔다.
  const [signupToken] = useState(pendingSignup.get);
  const { nickname, changeNickname, error, submitting, submit } = useSignup(signupToken);

  if (signupToken === null) {
    return <Redirect href="/login" />;
  }
  return (
    <Screen scroll>
      <View style={styles.back}>
        <Button
          label="뒤로"
          variant="text"
          disabled={submitting}
          onPress={() => router.dismissTo('/login')}
        />
      </View>
      <View style={{ gap: space(2), marginTop: space(6), marginBottom: space(8) }}>
        <FadeInItem index={0}>
          <AppText variant="display" accessibilityRole="header">
            어떻게 불러 드릴까요?
          </AppText>
        </FadeInItem>
        <FadeInItem index={1}>
          <AppText tone="muted">벙과 댓글에서 동호회 사람들에게 보이는 이름이에요.</AppText>
        </FadeInItem>
      </View>
      <FadeInItem index={2}>
        <TextField
          label="닉네임"
          value={nickname}
          onChangeText={changeNickname}
          error={error}
          helper="2~16자, 한글·영문·숫자·밑줄(_)"
          autoCapitalize="none"
          autoCorrect={false}
          autoFocus
          editable={!submitting}
          returnKeyType="done"
          onSubmitEditing={() => void submit()}
        />
      </FadeInItem>
      <View style={styles.spacer} />
      <FadeInItem index={3} style={{ marginTop: space(6) }}>
        <Button label="가입하기" loading={submitting} onPress={() => void submit()} />
      </FadeInItem>
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { alignSelf: 'flex-start', marginLeft: -24 },
  spacer: { flex: 1 },
});
