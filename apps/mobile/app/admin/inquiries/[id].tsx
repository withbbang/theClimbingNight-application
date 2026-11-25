import { useState } from 'react';
import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { ChatCircleTextIcon } from 'phosphor-react-native/src/icons/ChatCircleText';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { FreshQuery } from '@/components/ui/FreshQuery';
import { Notice } from '@/components/ui/Notice';
import { TextField } from '@/components/ui/TextField';
import { useAdminInquiry, useAnswerInquiry } from '@/hooks/use-inquiry-admin';
import { useLeft } from '@/hooks/use-left';
import { useTheme } from '@/hooks/use-theme';
import { isNotFound } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { formatDateTime } from '@/lib/kst';
import { parseId } from '@/lib/parse-id';
import type { InquiryAdminResponse } from '@/types/api/inquiry';

function Answer({ inquiry }: { inquiry: InquiryAdminResponse }) {
  const router = useRouter();
  const { colors, radius, space } = useTheme();
  const [text, setText] = useState(inquiry.answer ?? '');
  const [tried, setTried] = useState(false);
  const left = useLeft();
  const answer = useAnswerInquiry(inquiry.id);
  const editing = inquiry.status === 'ANSWERED';
  const trimmed = text.trim();

  async function submit() {
    setTried(true);
    if (trimmed && (await answer.submit(trimmed)) && !left.current) {
      router.dismissTo('/admin/inquiries');
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem
        index={1}
        style={{
          gap: space(2),
          padding: space(4),
          borderRadius: radius.card,
          backgroundColor: colors.surface,
        }}
      >
        <AppText variant="title">{inquiry.title}</AppText>
        <AppText variant="caption" tone="muted">
          {`${inquiry.nickname ?? '탈퇴한 회원'}, ${formatDateTime(inquiry.createdAt)}`}
        </AppText>
        <AppText selectable>{inquiry.body}</AppText>
      </FadeInItem>
      <FadeInItem index={2}>
        <TextField
          label="답변"
          value={text}
          onChangeText={setText}
          maxLength={2000}
          multiline
          editable={!answer.isPending}
          error={tried && !trimmed ? '답변을 입력해 주세요.' : null}
          helper={
            editing
              ? '답변을 고쳐도 알림은 다시 가지 않아요.'
              : '보내면 문의한 사람에게 알림이 가요.'
          }
        />
      </FadeInItem>
      {answer.error ? <ErrorBanner message={errorMessage(answer.error)} /> : null}
      <FadeInItem index={3}>
        <Button
          label={editing ? '답변 고치기' : '답변 보내기'}
          loading={answer.isPending}
          onPress={() => void submit()}
        />
      </FadeInItem>
    </View>
  );
}

export default function AdminInquiryScreen() {
  const router = useRouter();
  const id = parseId(useLocalSearchParams().id);
  const query = useAdminInquiry(id);
  return (
    <AdminScreen title="문의 답변" scroll>
      {id === null || isNotFound(query.error) ? (
        <Notice
          illustration={<FloatingIcon icon={ChatCircleTextIcon} />}
          title="문의를 찾을 수 없어요"
          description="주소가 잘못됐거나 없는 문의예요."
          actionLabel="홈으로"
          onAction={() => router.dismissTo('/')}
        />
      ) : (
        <FreshQuery key={id} query={query}>
          {(inquiry) => <Answer inquiry={inquiry} />}
        </FreshQuery>
      )}
    </AdminScreen>
  );
}
