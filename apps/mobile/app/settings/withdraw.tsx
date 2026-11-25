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
import { Toggle } from '@/components/ui/Toggle';
import { useWithdraw } from '@/hooks/use-profile';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';

const CONSEQUENCES = [
  { title: '지워져요', body: '닉네임과 소셜 로그인 연결, 인증 신청, 문의와 답변, 알림 설정' },
  { title: '취소돼요', body: '다가오는 벙 신청' },
  { title: '남아요', body: '동호회 명부와 통계에 쓰는 활동 기록. 앱 계정과의 연결은 끊어요.' },
];

function Withdraw() {
  const router = useRouter();
  const { space } = useTheme();
  const [confirmed, setConfirmed] = useState(false);
  const withdraw = useWithdraw();

  async function submit() {
    if (await withdraw.submit()) {
      router.dismissTo('/');
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0}>
        <AppText variant="display" accessibilityRole="header">
          탈퇴하면
        </AppText>
      </FadeInItem>
      {CONSEQUENCES.map(({ title, body }, i) => (
        <FadeInItem key={title} index={i + 1}>
          <AppText variant="label">{title}</AppText>
          <AppText tone="muted">{body}</AppText>
        </FadeInItem>
      ))}
      <FadeInItem index={4}>
        <Toggle
          label="내용을 확인했어요"
          value={confirmed}
          disabled={withdraw.isPending}
          onChange={setConfirmed}
        />
      </FadeInItem>
      {withdraw.error ? <ErrorBanner message={errorMessage(withdraw.error)} /> : null}
      <FadeInItem index={5}>
        <Button
          label="탈퇴하기"
          variant="secondary"
          disabled={!confirmed}
          loading={withdraw.isPending}
          onPress={() => void submit()}
        />
      </FadeInItem>
    </View>
  );
}

export default function WithdrawScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        <Withdraw />
      </RoleGate>
    </Screen>
  );
}
