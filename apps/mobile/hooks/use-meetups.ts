import { useQuery } from '@tanstack/react-query';

import { useIsMember } from '@/hooks/use-role';
import { addDays, todayKst, type DateKey } from '@/lib/kst';
import { meetupApi } from '@/lib/meetup-api';

/** 서버가 한 번에 허용하는 최대 범위. */
const MAX_RANGE_DAYS = 62;

export function useMeetups(from: DateKey, to: DateKey) {
  return useQuery({
    queryKey: ['meetups', 'list', from, to],
    queryFn: () => meetupApi.list(from, to),
    enabled: useIsMember(),
  });
}

export function useUpcomingMeetups() {
  const today = todayKst();
  return useMeetups(today, addDays(today, MAX_RANGE_DAYS));
}

export function useMeetup(id: number | null) {
  const isMember = useIsMember();
  return useQuery({
    queryKey: ['meetups', 'detail', id],
    queryFn: () => meetupApi.get(id ?? 0),
    enabled: isMember && id !== null,
  });
}
