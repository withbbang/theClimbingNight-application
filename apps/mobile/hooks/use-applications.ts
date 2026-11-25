import { useQuery, useQueryClient } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { useIsAdmin } from '@/hooks/use-role';
import { attendanceApi } from '@/lib/attendance-api';
import { meetupApi } from '@/lib/meetup-api';
import type { AttendanceResult } from '@/types/api/attendance';
import type { MeetupApplicationAdminResponse } from '@/types/api/meetup';

const keyOf = (meetupId: number | null) => ['meetups', 'applications', meetupId];

type OnError = (error: Error) => void;

export function useApplications(meetupId: number | null) {
  const admin = useIsAdmin();
  return useQuery({
    queryKey: keyOf(meetupId),
    queryFn: () => meetupApi.applications(meetupId ?? 0),
    enabled: admin && meetupId !== null,
  });
}

export function useRemoveApplication(onError: OnError) {
  return useAdminMutation(
    // 204라 값이 없으므로 성공을 true로 알린다.
    (id: number) => meetupApi.removeApplication(id).then(() => true),
    [['meetups']],
    { error: onError },
  );
}

export function useAddLateCancelPenalty(onError: OnError) {
  return useAdminMutation(meetupApi.addLateCancelPenalty, [['meetups'], ['penalties']], {
    error: onError,
  });
}

export function useCorrectAttendance(meetupId: number, onError: OnError) {
  const client = useQueryClient();
  return useAdminMutation(
    ({ id, result }: { id: number; result: AttendanceResult }) => attendanceApi.correct(id, result),
    [['meetups'], ['penalties'], ['attendance-rates']],
    {
      // 다시 받는 동안 옛 결과로 되돌아가 보이지 않게 한다.
      success: (saved, { id }) =>
        client.setQueryData<MeetupApplicationAdminResponse[]>(keyOf(meetupId), (old) =>
          old?.map((row) =>
            row.attendanceId === id ? { ...row, attendanceResult: saved.result } : row,
          ),
        ),
      error: onError,
    },
  );
}
