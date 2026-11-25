import { Pressable, StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { ChatCircleTextIcon } from 'phosphor-react-native/src/icons/ChatCircleText';
import Animated from 'react-native-reanimated';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Hold } from '@/components/ui/Hold';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { Screen } from '@/components/ui/Screen';
import { useMyInquiries } from '@/hooks/use-inquiries';
import { usePressScale } from '@/hooks/use-press-scale';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/kst';
import type { InquiryResponse } from '@/types/api/inquiry';

const MAX_STAGGER = 8;

function InquiryRow({ inquiry, index }: { inquiry: InquiryResponse; index: number }) {
  const router = useRouter();
  const { colors, radius, space } = useTheme();
  const press = usePressScale();
  const answered = inquiry.status === 'ANSWERED';
  return (
    <FadeInItem index={Math.min(index, MAX_STAGGER)} style={{ marginBottom: space(3) }}>
      <Animated.View style={press.style}>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push(`/inquiries/${inquiry.id}`)}
          {...press.handlers}
          style={[
            styles.row,
            {
              gap: space(3),
              padding: space(4),
              borderRadius: radius.card,
              backgroundColor: colors.surface,
            },
          ]}
        >
          <View style={styles.fill}>
            <AppText variant="label">{inquiry.title}</AppText>
            <AppText variant="caption" tone="muted">
              {formatDateTime(inquiry.createdAt)}
            </AppText>
          </View>
          <Hold on={answered} />
          <AppText variant="caption" tone={answered ? 'default' : 'muted'}>
            {answered ? '답변 완료' : '답변 대기'}
          </AppText>
        </Pressable>
      </Animated.View>
    </FadeInItem>
  );
}

function Inquiries() {
  const router = useRouter();
  const { space } = useTheme();
  const query = useMyInquiries();

  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      <AppText variant="display" accessibilityRole="header">
        문의
      </AppText>
      <PagedList
        query={query}
        label="문의 목록"
        keyOf={(inquiry) => inquiry.id}
        renderItem={(inquiry, index) => <InquiryRow inquiry={inquiry} index={index} />}
        empty={
          <Notice
            illustration={<FloatingIcon icon={ChatCircleTextIcon} />}
            title="아직 문의가 없어요"
            description="궁금한 점을 남기면 운영진이 답해 드려요."
          />
        }
      />
      <Button label="문의하기" onPress={() => router.push('/inquiries/new')} />
    </View>
  );
}

export default function InquiriesScreen() {
  return (
    <Screen>
      <BackButton />
      <RoleGate role="USER">
        <Inquiries />
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
});
