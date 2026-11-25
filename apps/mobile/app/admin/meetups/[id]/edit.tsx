import { useLocalSearchParams, useRouter } from 'expo-router';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { MeetupForm } from '@/components/meetup/MeetupForm';
import { MeetupNotFound } from '@/components/meetup/MeetupNotFound';
import { FreshQuery } from '@/components/ui/FreshQuery';
import { useLeft } from '@/hooks/use-left';
import { useUpdateMeetup } from '@/hooks/use-meetup-admin';
import { useMeetup } from '@/hooks/use-meetups';
import { isNotFound } from '@/lib/api-error';
import { toSchedule, type MeetupFormValues } from '@/lib/meetup-form';
import { parseId } from '@/lib/parse-id';
import type { MeetupResponse, MeetupWriteRequest } from '@/types/api/meetup';

const toValues = (meetup: MeetupResponse): MeetupFormValues => ({
  ...toSchedule(meetup.startsAt, meetup.endsAt),
  title: meetup.title,
  placeName: meetup.placeName ?? '',
  description: meetup.description ?? '',
  capacity: meetup.capacity === null ? '' : String(meetup.capacity),
  lateCancelHours: String(meetup.lateCancelHours),
  participantsVisible: meetup.participantsVisible,
});

function EditMeetup({ meetup }: { meetup: MeetupResponse }) {
  const router = useRouter();
  const left = useLeft();
  const update = useUpdateMeetup(meetup.id);

  async function submit(body: MeetupWriteRequest) {
    if ((await update.submit(body)) && !left.current) {
      router.dismissTo(`/meetups/${meetup.id}`);
    }
  }

  return (
    <MeetupForm
      initial={toValues(meetup)}
      submitLabel="저장"
      pending={update.isPending}
      error={update.error}
      onSubmit={(body) => void submit(body)}
    />
  );
}

export default function EditMeetupScreen() {
  const id = parseId(useLocalSearchParams().id);
  const query = useMeetup(id, true);
  return (
    <AdminScreen title="벙 수정" scroll>
      {id === null || isNotFound(query.error) ? (
        <MeetupNotFound />
      ) : (
        <FreshQuery key={id} query={query}>
          {({ meetup }) => <EditMeetup meetup={meetup} />}
        </FreshQuery>
      )}
    </AdminScreen>
  );
}
