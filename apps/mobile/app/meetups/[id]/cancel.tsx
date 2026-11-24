import { useState } from 'react';
import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { RoleGate } from '@/components/auth/RoleGate';
import { MeetupNotFound } from '@/components/meetup/MeetupNotFound';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { TextField } from '@/components/ui/TextField';
import { useCancelApplication } from '@/hooks/use-meetup-application';
import { useMeetup } from '@/hooks/use-meetups';
import { useNow } from '@/hooks/use-now';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import { formatDateTime } from '@/lib/kst';
import { parseId } from '@/lib/parse-id';
import type { MeetupDetailResponse } from '@/types/api/meetup';

const HOUR_MS = 60 * 60 * 1000;

function CancelForm({ detail }: { detail: MeetupDetailResponse }) {
  const router = useRouter();
  const { space } = useTheme();
  const { meetup, my } = detail;
  const [reason, setReason] = useState('');
  const cancel = useCancelApplication(meetup.id);
  const deadline = Date.parse(meetup.startsAt) - meetup.lateCancelHours * HOUR_MS;
  const now = useNow([deadline]);
  // 기준 뒤에 승격된 사람은 늦은 취소가 아니다. 판정은 서버가 하므로 단정하지 않는다.
  const maybeLate = my?.status === 'CONFIRMED' && now >= deadline;
  const back = () => router.dismissTo(`/meetups/${meetup.id}`);

  async function submit() {
    if (await cancel.submit(reason)) {
      back();
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0} style={{ gap: space(2) }}>
        <AppText variant="display" accessibilityRole="header">
          신청을 취소할까요?
        </AppText>
        <AppText tone="muted">{`${meetup.title}, ${formatDateTime(meetup.startsAt)}`}</AppText>
      </FadeInItem>
      {maybeLate ? (
        <FadeInItem index={1}>
          <AppText tone="danger" accessibilityRole="alert">
            취소 기준 시각이 지났어요. 늦은 취소로 표시될 수 있어요.
          </AppText>
        </FadeInItem>
      ) : null}
      <FadeInItem index={2}>
        <TextField
          label="취소 사유 (선택)"
          value={reason}
          onChangeText={setReason}
          maxLength={200}
          multiline
          editable={!cancel.isPending}
          helper="운영진만 볼 수 있어요."
        />
      </FadeInItem>
      {cancel.error ? <ErrorBanner message={errorMessage(cancel.error)} /> : null}
      <FadeInItem index={3} style={{ gap: space(2) }}>
        <Button label="취소 확정" loading={cancel.isPending} onPress={() => void submit()} />
        <Button label="돌아가기" variant="text" disabled={cancel.isPending} onPress={back} />
      </FadeInItem>
    </View>
  );
}

export default function MeetupCancelScreen() {
  const id = parseId(useLocalSearchParams().id);
  const query = useMeetup(id);
  return (
    <Screen scroll>
      <RoleGate role="MEMBER">
        {id === null ? (
          <MeetupNotFound />
        ) : (
          <QueryState query={query}>{(detail) => <CancelForm detail={detail} />}</QueryState>
        )}
      </RoleGate>
    </Screen>
  );
}
