import type { MeetupStatus } from '@/types/api/meetup';

export type MeetupPhase = 'CANCELED' | 'UPCOMING' | 'ONGOING' | 'ENDED';

interface Schedule {
  startsAt: string;
  endsAt: string | null;
  status: MeetupStatus;
}

export function meetupPhase(meetup: Schedule, now: Date): MeetupPhase {
  if (meetup.status === 'CANCELED') {
    return 'CANCELED';
  }
  const start = Date.parse(meetup.startsAt);
  if (now.getTime() < start) {
    return 'UPCOMING';
  }
  const end = meetup.endsAt === null ? start : Date.parse(meetup.endsAt);
  return now.getTime() < end ? 'ONGOING' : 'ENDED';
}

/** 단계가 바뀌는 시각들. */
export function phaseBoundaries({ startsAt, endsAt }: Schedule): number[] {
  return [startsAt, endsAt].flatMap((time) => (time === null ? [] : Date.parse(time)));
}

export function capacityText(confirmed: number, capacity: number | null) {
  return capacity === null ? `확정 ${confirmed}` : `확정 ${confirmed}/${capacity}`;
}
