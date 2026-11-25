import { useState } from 'react';

import { useRouter } from 'expo-router';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { MeetupForm } from '@/components/meetup/MeetupForm';
import { useLeft } from '@/hooks/use-left';
import { useCreateMeetup } from '@/hooks/use-meetup-admin';
import { todayKst } from '@/lib/kst';
import type { MeetupFormValues } from '@/lib/meetup-form';
import type { MeetupWriteRequest } from '@/types/api/meetup';

const emptyForm = (): MeetupFormValues => ({
  title: '',
  placeName: '',
  description: '',
  day: todayKst(),
  start: '',
  end: '',
  capacity: '',
  lateCancelHours: '24',
  participantsVisible: true,
});

function NewMeetup() {
  const router = useRouter();
  const [initial] = useState(emptyForm);
  const left = useLeft();
  const create = useCreateMeetup();

  async function submit(body: MeetupWriteRequest) {
    const created = await create.submit(body);
    if (created && !left.current) {
      router.replace(`/meetups/${created.id}`);
    }
  }

  return (
    <MeetupForm
      initial={initial}
      submitLabel="벙 열기"
      pending={create.isPending}
      error={create.error}
      onSubmit={(body) => void submit(body)}
    />
  );
}

export default function NewMeetupScreen() {
  return (
    <AdminScreen title="새 벙" scroll>
      <NewMeetup />
    </AdminScreen>
  );
}
