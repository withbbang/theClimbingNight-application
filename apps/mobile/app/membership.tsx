import { useState } from 'react';
import { View } from 'react-native';

import { HourglassMediumIcon } from 'phosphor-react-native/src/icons/HourglassMedium';
import { SealCheckIcon } from 'phosphor-react-native/src/icons/SealCheck';
import Animated, { useReducedMotion, ZoomIn } from 'react-native-reanimated';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { TextField } from '@/components/ui/TextField';
import { useApplyMembership, useMyMembershipRequest } from '@/hooks/use-membership';
import { useIsMember } from '@/hooks/use-role';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';

function ApplyForm({ rejectReason }: { rejectReason: string | null }) {
  const { space } = useTheme();
  const [nickname, setNickname] = useState('');
  const [blank, setBlank] = useState(false);
  const apply = useApplyMembership();

  function submit() {
    const trimmed = nickname.trim();
    setBlank(trimmed === '');
    if (trimmed !== '') {
      void apply.submit(trimmed);
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0} style={{ gap: space(2) }}>
        <AppText variant="display" accessibilityRole="header">
          동호회원 인증
        </AppText>
        <AppText tone="muted">소모임에서 쓰는 닉네임으로 운영진이 확인해요.</AppText>
      </FadeInItem>
      {rejectReason ? (
        <FadeInItem index={1} style={{ gap: space(1) }}>
          <AppText variant="label">지난 신청이 거절됐어요</AppText>
          <AppText tone="danger">{rejectReason}</AppText>
        </FadeInItem>
      ) : null}
      <FadeInItem index={2}>
        <TextField
          label="소모임 닉네임"
          value={nickname}
          onChangeText={(text) => {
            setNickname(text);
            setBlank(false);
          }}
          error={blank ? '소모임 닉네임을 입력해 주세요.' : null}
          maxLength={50}
          autoCorrect={false}
          editable={!apply.isPending}
          returnKeyType="done"
          onSubmitEditing={submit}
        />
      </FadeInItem>
      {apply.error ? <ErrorBanner message={errorMessage(apply.error)} /> : null}
      <FadeInItem index={3}>
        <Button label="인증 신청하기" loading={apply.isPending} onPress={submit} />
      </FadeInItem>
    </View>
  );
}

function Verification() {
  const isMember = useIsMember();
  const reduced = useReducedMotion();
  const query = useMyMembershipRequest();
  const entering = reduced ? undefined : ZoomIn.springify().damping(12);

  if (isMember) {
    return (
      <Notice
        illustration={
          <Animated.View entering={entering}>
            <FloatingIcon icon={SealCheckIcon} />
          </Animated.View>
        }
        title="동호회원 인증을 마쳤어요"
        description="이제 벙에 신청할 수 있어요."
      />
    );
  }
  return (
    <QueryState query={query}>
      {(request) =>
        request?.status === 'PENDING' ? (
          <Notice
            illustration={
              <Animated.View entering={entering}>
                <FloatingIcon icon={HourglassMediumIcon} />
              </Animated.View>
            }
            title="운영진이 확인하고 있어요"
            description={`소모임 닉네임 '${request.somoimNickname}'으로 신청했어요. 결과는 알림으로 알려 드려요.`}
          />
        ) : (
          <ApplyForm rejectReason={request?.status === 'REJECTED' ? request.rejectReason : null} />
        )
      }
    </QueryState>
  );
}

export default function MembershipScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        <Verification />
      </RoleGate>
    </Screen>
  );
}
