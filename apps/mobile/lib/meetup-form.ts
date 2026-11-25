import { addDays, formatTime, parseTime, toDateKey, toIso, type DateKey } from '@/lib/kst';
import type { MeetupWriteRequest } from '@/types/api/meetup';

export const MAX_CAPACITY = 500;
export const MAX_LATE_CANCEL_HOURS = 168;

/** 입력 중인 일정. 시각은 적은 그대로 둔다. */
export interface Schedule {
  day: DateKey;
  start: string;
  end: string;
}

export interface MeetupFormValues extends Schedule {
  title: string;
  placeName: string;
  description: string;
  capacity: string;
  lateCancelHours: string;
  participantsVisible: boolean;
}

export type MeetupFormErrors = Partial<
  Record<'title' | 'placeName' | 'start' | 'end' | 'capacity' | 'lateCancelHours', string>
>;

export function toSchedule(startsAt: string, endsAt: string | null): Schedule {
  return {
    day: toDateKey(startsAt),
    start: formatTime(startsAt),
    end: endsAt === null ? '' : formatTime(endsAt),
  };
}

/** 종료가 시작보다 이르거나 같으면 다음 날이다. */
export function endsNextDay({ start, end }: Schedule): boolean {
  const from = parseTime(start);
  const to = parseTime(end);
  return from !== null && to !== null && to <= from;
}

export function scheduleToIso(schedule: Schedule): { startsAt: string; endsAt: string } | null {
  const start = parseTime(schedule.start);
  const end = parseTime(schedule.end);
  if (start === null || end === null) {
    return null;
  }
  const endDay = endsNextDay(schedule) ? addDays(schedule.day, 1) : schedule.day;
  return { startsAt: toIso(schedule.day, start), endsAt: toIso(endDay, end) };
}

const TIME_ERROR = '1930처럼 네 자리로 적어 주세요.';

export function scheduleErrors({ start, end }: Schedule): MeetupFormErrors {
  return {
    ...(parseTime(start) === null ? { start: TIME_ERROR } : {}),
    ...(parseTime(end) === null ? { end: TIME_ERROR } : {}),
  };
}

function parseInteger(text: string, min: number, max: number): number | null {
  const value = /^\d+$/.test(text.trim()) ? Number(text) : NaN;
  return value >= min && value <= max ? value : null;
}

export function validateMeetup(values: MeetupFormValues): {
  body: MeetupWriteRequest | null;
  errors: MeetupFormErrors;
} {
  const title = values.title.trim();
  const placeName = values.placeName.trim();
  const schedule = scheduleToIso(values);
  const capacity = parseInteger(values.capacity, 1, MAX_CAPACITY);
  const lateCancelHours = parseInteger(values.lateCancelHours, 0, MAX_LATE_CANCEL_HOURS);
  const errors: MeetupFormErrors = {
    ...(title ? {} : { title: '제목을 입력해 주세요.' }),
    ...(placeName ? {} : { placeName: '장소를 입력해 주세요.' }),
    ...scheduleErrors(values),
    ...(capacity === null ? { capacity: `정원은 1명에서 ${MAX_CAPACITY}명까지예요.` } : {}),
    ...(lateCancelHours === null
      ? { lateCancelHours: `0시간에서 ${MAX_LATE_CANCEL_HOURS}시간까지예요.` }
      : {}),
  };
  if (
    Object.keys(errors).length > 0 ||
    !schedule ||
    capacity === null ||
    lateCancelHours === null
  ) {
    return { body: null, errors };
  }
  return {
    errors,
    body: {
      title,
      placeName,
      description: values.description.trim() || null,
      ...schedule,
      capacity,
      lateCancelHours,
      participantsVisible: values.participantsVisible,
    },
  };
}
