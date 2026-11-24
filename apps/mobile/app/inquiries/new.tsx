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
import { useCreateInquiry } from '@/hooks/use-inquiries';
import { useLeft } from '@/hooks/use-left';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';

function InquiryForm() {
  const router = useRouter();
  const { space } = useTheme();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [tried, setTried] = useState(false);
  const left = useLeft();
  const create = useCreateInquiry();

  async function submit() {
    setTried(true);
    const inquiry = { title: title.trim(), body: body.trim() };
    if (inquiry.title && inquiry.body && (await create.submit(inquiry)) && !left.current) {
      router.dismissTo('/inquiries');
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0}>
        <AppText variant="display" accessibilityRole="header">
          무엇이 궁금하세요?
        </AppText>
        <AppText tone="muted">나와 운영진만 볼 수 있어요.</AppText>
      </FadeInItem>
      <FadeInItem index={1}>
        <TextField
          label="제목"
          value={title}
          onChangeText={setTitle}
          maxLength={100}
          editable={!create.isPending}
          error={tried && !title.trim() ? '제목을 입력해 주세요.' : null}
        />
      </FadeInItem>
      <FadeInItem index={2}>
        <TextField
          label="내용"
          value={body}
          onChangeText={setBody}
          maxLength={2000}
          multiline
          editable={!create.isPending}
          error={tried && !body.trim() ? '내용을 입력해 주세요.' : null}
        />
      </FadeInItem>
      {create.error ? <ErrorBanner message={errorMessage(create.error)} /> : null}
      <FadeInItem index={3}>
        <Button label="보내기" loading={create.isPending} onPress={() => void submit()} />
      </FadeInItem>
    </View>
  );
}

export default function NewInquiryScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        <InquiryForm />
      </RoleGate>
    </Screen>
  );
}
