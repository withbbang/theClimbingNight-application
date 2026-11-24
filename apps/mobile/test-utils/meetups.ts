import type { MeetupDetailResponse, MeetupSummaryResponse } from '@/types/api/meetup';

export function summary(over: Partial<MeetupSummaryResponse> = {}): MeetupSummaryResponse {
  return {
    id: 1,
    title: '화요 정기벙',
    placeName: '더클라임 연남',
    startsAt: '2026-11-25T10:30:00Z',
    endsAt: '2026-11-25T13:30:00Z',
    capacity: 12,
    confirmedCount: 8,
    waitlistCount: 0,
    status: 'OPEN',
    myStatus: null,
    ...over,
  };
}

export function detail(
  over: Partial<Omit<MeetupDetailResponse, 'meetup'>> & {
    meetup?: Partial<MeetupDetailResponse['meetup']>;
  } = {},
): MeetupDetailResponse {
  return {
    confirmedCount: 8,
    waitlistCount: 0,
    my: null,
    participants: [],
    ...over,
    meetup: {
      id: 1,
      title: '화요 정기벙',
      description: '가볍게 풀어요',
      placeName: '더클라임 연남',
      startsAt: '2026-11-26T10:30:00Z',
      endsAt: '2026-11-26T13:30:00Z',
      capacity: 12,
      lateCancelHours: 24,
      participantsVisible: true,
      status: 'OPEN',
      canceledAt: null,
      host: { id: 9, nickname: '달이', role: 'ADMIN' },
      ...over.meetup,
    },
  };
}
