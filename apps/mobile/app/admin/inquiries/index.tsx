import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { ChatCircleTextIcon } from 'phosphor-react-native/src/icons/ChatCircleText';
import Animated, { FadeInLeft, FadeInRight, useReducedMotion } from 'react-native-reanimated';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { AppText } from '@/components/ui/AppText';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { PressCard } from '@/components/ui/PressCard';
import { Segments } from '@/components/ui/Segments';
import { useAdminInquiries } from '@/hooks/use-inquiry-admin';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/kst';
import type { InquiryResponse } from '@/types/api/inquiry';

type Status = InquiryResponse['status'];

const STATUSES = [
  { value: 'OPEN', label: '답변 대기' },
  { value: 'ANSWERED', label: '답변 완료' },
] as const;

function Inquiries({ status }: { status: Status }) {
  const router = useRouter();
  const query = useAdminInquiries(status);
  return (
    <PagedList
      query={query}
      label="문의 목록"
      keyOf={(inquiry) => inquiry.id}
      renderItem={(inquiry, index) => {
        const name = inquiry.nickname ?? '탈퇴한 회원';
        const created = formatDateTime(inquiry.createdAt);
        return (
          <PressCard
            label={`${inquiry.title}, ${name}, ${created}`}
            index={index}
            onPress={() => router.push(`/admin/inquiries/${inquiry.id}`)}
          >
            <View style={styles.fill}>
              <AppText variant="label">{inquiry.title}</AppText>
              <AppText variant="caption" tone="muted">
                {`${name}, ${created}`}
              </AppText>
            </View>
          </PressCard>
        );
      }}
      empty={
        <Notice
          illustration={<FloatingIcon icon={ChatCircleTextIcon} />}
          title={status === 'OPEN' ? '답변을 기다리는 문의가 없어요' : '답변한 문의가 없어요'}
          description={
            status === 'OPEN' ? '새 문의가 오면 여기에 보여요.' : '답변을 보내면 여기에 보여요.'
          }
        />
      }
    />
  );
}

function AdminInquiries() {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  const [status, setStatus] = useState<Status>('OPEN');
  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      <Segments options={STATUSES} value={status} onChange={setStatus} />
      <Animated.View
        key={status}
        style={styles.fill}
        entering={
          reduced
            ? undefined
            : (status === 'OPEN' ? FadeInLeft : FadeInRight).springify().damping(20)
        }
      >
        <Inquiries status={status} />
      </Animated.View>
    </View>
  );
}

export default function AdminInquiriesScreen() {
  return (
    <AdminScreen title="문의 답변">
      <AdminInquiries />
    </AdminScreen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
