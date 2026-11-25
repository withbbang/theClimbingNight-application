import { useState } from 'react';
import { View } from 'react-native';

import { ScheduleFields } from '@/components/meetup/ScheduleFields';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { TextField } from '@/components/ui/TextField';
import { Toggle } from '@/components/ui/Toggle';
import { useTheme } from '@/hooks/use-theme';
import { validateMeetup, type MeetupFormValues } from '@/lib/meetup-form';
import type { MeetupWriteRequest } from '@/types/api/meetup';

interface MeetupFormProps {
  initial: MeetupFormValues;
  submitLabel: string;
  pending: boolean;
  error: string | null;
  onSubmit: (body: MeetupWriteRequest) => void;
}

export function MeetupForm({ initial, submitLabel, pending, error, onSubmit }: MeetupFormProps) {
  const { space } = useTheme();
  const [values, setValues] = useState(initial);
  const [tried, setTried] = useState(false);
  const result = validateMeetup(values);
  const errors = tried ? result.errors : {};
  const change = (over: Partial<MeetupFormValues>) => setValues((old) => ({ ...old, ...over }));
  const editable = !pending;

  function submit() {
    setTried(true);
    if (result.body) {
      onSubmit(result.body);
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={1}>
        <TextField
          label="제목"
          value={values.title}
          onChangeText={(title) => change({ title })}
          maxLength={100}
          editable={editable}
          error={errors.title}
        />
      </FadeInItem>
      <FadeInItem index={2}>
        <TextField
          label="장소"
          value={values.placeName}
          onChangeText={(placeName) => change({ placeName })}
          maxLength={100}
          editable={editable}
          error={errors.placeName}
        />
      </FadeInItem>
      <FadeInItem index={3}>
        <ScheduleFields value={values} onChange={change} errors={errors} editable={editable} />
      </FadeInItem>
      <FadeInItem index={4}>
        <TextField
          label="정원"
          value={values.capacity}
          onChangeText={(capacity) => change({ capacity })}
          keyboardType="number-pad"
          maxLength={3}
          editable={editable}
          error={errors.capacity}
        />
      </FadeInItem>
      <FadeInItem index={5}>
        <TextField
          label="늦은 취소 기준 (시작 몇 시간 전)"
          value={values.lateCancelHours}
          onChangeText={(lateCancelHours) => change({ lateCancelHours })}
          keyboardType="number-pad"
          maxLength={3}
          editable={editable}
          error={errors.lateCancelHours}
        />
      </FadeInItem>
      <FadeInItem index={6}>
        <Toggle
          label="신청자 명단 공개"
          description="끄면 동호회원에게는 인원만 보여요."
          value={values.participantsVisible}
          disabled={pending}
          onChange={(participantsVisible) => change({ participantsVisible })}
        />
      </FadeInItem>
      <FadeInItem index={7}>
        <TextField
          label="설명 (선택)"
          value={values.description}
          onChangeText={(description) => change({ description })}
          maxLength={5000}
          multiline
          editable={editable}
        />
      </FadeInItem>
      {error ? <ErrorBanner message={error} /> : null}
      <FadeInItem index={8}>
        <Button label={submitLabel} loading={pending} onPress={submit} />
      </FadeInItem>
    </View>
  );
}
