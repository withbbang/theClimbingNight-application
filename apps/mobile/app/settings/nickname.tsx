import { useState } from 'react';
import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { Screen } from '@/components/ui/Screen';
import { TextField } from '@/components/ui/TextField';
import { useLeft } from '@/hooks/use-left';
import { useChangeNickname } from '@/hooks/use-profile';
import { useSession } from '@/hooks/use-session';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { nicknameError } from '@/lib/nickname';

function NicknameForm() {
  const router = useRouter();
  const { space } = useTheme();
  const session = useSession();
  const current = session.status === 'authenticated' ? session.tokens.user.nickname : '';
  const [nickname, setNickname] = useState(current);
  const [invalid, setInvalid] = useState<string | null>(null);
  const left = useLeft();
  const change = useChangeNickname();
  const ofField = change.error instanceof ApiError && change.error.code.startsWith('USER_NICKNAME');

  async function submit() {
    const next = nickname.trim();
    const error = nicknameError(next);
    setInvalid(error);
    if (error) {
      return;
    }
    if ((next === current || (await change.submit(next))) && !left.current) {
      router.dismissTo('/my');
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0}>
        <AppText variant="display" accessibilityRole="header">
          닉네임 변경
        </AppText>
      </FadeInItem>
      <FadeInItem index={1}>
        <TextField
          label="닉네임"
          value={nickname}
          onChangeText={(text) => {
            setNickname(text);
            setInvalid(null);
            change.reset();
          }}
          error={invalid ?? (ofField ? errorMessage(change.error) : null)}
          helper="2~16자, 한글·영문·숫자·밑줄(_)"
          autoCapitalize="none"
          autoCorrect={false}
          editable={!change.isPending}
          returnKeyType="done"
          onSubmitEditing={() => void submit()}
        />
      </FadeInItem>
      {change.error && !ofField ? <ErrorBanner message={errorMessage(change.error)} /> : null}
      <FadeInItem index={2}>
        <Button label="저장" loading={change.isPending} onPress={() => void submit()} />
      </FadeInItem>
    </View>
  );
}

export default function NicknameScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        <NicknameForm />
      </RoleGate>
    </Screen>
  );
}
