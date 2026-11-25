import { useQuery } from '@tanstack/react-query';

import { useIsMember } from '@/hooks/use-role';
import { useToday } from '@/hooks/use-now';
import { addDays, type DateKey } from '@/lib/kst';
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
  const today = useToday();
  return useMeetups(today, addDays(today, MAX_RANGE_DAYS));
}

/** fresh면 받은 지 얼마 안 됐어도 화면을 열 때 다시 받는다. */
export function useMeetup(id: number | null, fresh = false) {
  const isMember = useIsMember();
  return useQuery({
    queryKey: ['meetups', 'detail', id],
    queryFn: () => meetupApi.get(id ?? 0),
    enabled: isMember && id !== null,
    refetchOnMount: fresh ? 'always' : true,
  });
}
