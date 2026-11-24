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

export function capacityText(confirmed: number, capacity: number | null) {
  return capacity === null ? `확정 ${confirmed}` : `확정 ${confirmed}/${capacity}`;
}
