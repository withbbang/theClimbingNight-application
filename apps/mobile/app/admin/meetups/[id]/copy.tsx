import { useState } from 'react';
import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { MeetupNotFound } from '@/components/meetup/MeetupNotFound';
import { ScheduleFields } from '@/components/meetup/ScheduleFields';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FreshQuery } from '@/components/ui/FreshQuery';
import { useLeft } from '@/hooks/use-left';
import { useCopyMeetup } from '@/hooks/use-meetup-admin';
import { useMeetup } from '@/hooks/use-meetups';
import { useTheme } from '@/hooks/use-theme';
import { isNotFound } from '@/lib/api-error';
import { createErrorMessage } from '@/lib/error-message';
import { addDays, todayKst } from '@/lib/kst';
import { scheduleErrors, scheduleToIso, toSchedule, type Schedule } from '@/lib/meetup-form';
import { parseId } from '@/lib/parse-id';
import type { MeetupResponse } from '@/types/api/meetup';

/** 한 주 뒤 같은 시각. 이미 지난 날이면 오늘. */
function nextSchedule(meetup: MeetupResponse): Schedule {
  const schedule = toSchedule(meetup.startsAt, meetup.endsAt);
  const nextWeek = addDays(schedule.day, 7);
  const today = todayKst();
  return { ...schedule, day: nextWeek < today ? today : nextWeek };
}

function CopyMeetup({ meetup }: { meetup: MeetupResponse }) {
  const router = useRouter();
  const { space } = useTheme();
  const [schedule, setSchedule] = useState(() => nextSchedule(meetup));
  const [tried, setTried] = useState(false);
  const left = useLeft();
  const copy = useCopyMeetup(meetup.id);

  async function submit() {
    setTried(true);
    const body = scheduleToIso(schedule);
    const created = body ? await copy.submit(body) : undefined;
    if (created && !left.current) {
      router.replace(`/meetups/${created.id}`);
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={1} style={{ gap: space(1) }}>
        <AppText variant="title">{meetup.title}</AppText>
        <AppText tone="muted">제목, 장소, 정원, 설명은 그대로 두고 날짜만 새로 골라요.</AppText>
      </FadeInItem>
      <FadeInItem index={2}>
        <ScheduleFields
          value={schedule}
          onChange={(over) => setSchedule((old) => ({ ...old, ...over }))}
          errors={tried ? scheduleErrors(schedule) : {}}
          editable={!copy.isPending}
        />
      </FadeInItem>
      {copy.error ? <ErrorBanner message={createErrorMessage(copy.error)} /> : null}
      <FadeInItem index={3}>
        <Button label="이 날짜로 열기" loading={copy.isPending} onPress={() => void submit()} />
      </FadeInItem>
    </View>
  );
}

export default function CopyMeetupScreen() {
  const id = parseId(useLocalSearchParams().id);
  const query = useMeetup(id, true);
  return (
    <AdminScreen title="벙 복제" scroll>
      {id === null || isNotFound(query.error) ? (
        <MeetupNotFound />
      ) : (
        <FreshQuery key={id} query={query}>
          {({ meetup }) => <CopyMeetup meetup={meetup} />}
        </FreshQuery>
      )}
    </AdminScreen>
  );
}
