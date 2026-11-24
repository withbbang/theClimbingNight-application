import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { ChatCircleTextIcon } from 'phosphor-react-native/src/icons/ChatCircleText';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Hold } from '@/components/ui/Hold';
import { Notice } from '@/components/ui/Notice';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { useMyInquiry } from '@/hooks/use-inquiries';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-error';
import { formatDateTime } from '@/lib/kst';
import { parseId } from '@/lib/parse-id';
import type { InquiryResponse } from '@/types/api/inquiry';

function Conversation({ inquiry }: { inquiry: InquiryResponse }) {
  const { colors, radius, space } = useTheme();
  return (
    <View style={{ gap: space(6) }}>
      <FadeInItem index={0} style={{ gap: space(1) }}>
        <AppText variant="display" accessibilityRole="header">
          {inquiry.title}
        </AppText>
        <AppText variant="caption" tone="muted">
          {formatDateTime(inquiry.createdAt)}
        </AppText>
      </FadeInItem>
      <FadeInItem index={1}>
        <AppText selectable>{inquiry.body}</AppText>
      </FadeInItem>
      <FadeInItem
        key={inquiry.status}
        index={2}
        style={{
          gap: space(3),
          padding: space(5),
          borderRadius: radius.card,
          backgroundColor: colors.surface,
        }}
      >
        {inquiry.answer === null ? (
          <>
            <PulseHolds color={colors.accent} />
            <AppText variant="label">답변을 기다리고 있어요</AppText>
          </>
        ) : (
          <>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: space(2) }}>
              <Hold on size={18} />
              <AppText variant="label">운영진 답변</AppText>
            </View>
            <AppText selectable>{inquiry.answer}</AppText>
            {inquiry.answeredAt ? (
              <AppText variant="caption" tone="muted">
                {formatDateTime(inquiry.answeredAt)}
              </AppText>
            ) : null}
          </>
        )}
      </FadeInItem>
    </View>
  );
}

export default function InquiryScreen() {
  const router = useRouter();
  const id = parseId(useLocalSearchParams().id);
  const query = useMyInquiry(id);
  const missing = id === null || (query.error instanceof ApiError && query.error.status === 404);
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        {missing ? (
          <Notice
            illustration={<FloatingIcon icon={ChatCircleTextIcon} />}
            title="문의를 찾을 수 없어요"
            description="주소가 잘못됐거나 볼 수 없는 문의예요."
            actionLabel="홈으로"
            onAction={() => router.dismissTo('/')}
          />
        ) : (
          <QueryState query={query}>{(inquiry) => <Conversation inquiry={inquiry} />}</QueryState>
        )}
      </RoleGate>
    </Screen>
  );
}
