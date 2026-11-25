import { useQuery, useQueryClient } from '@tanstack/react-query';

import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { useIsAdmin } from '@/hooks/use-role';
import { attendanceApi } from '@/lib/attendance-api';
import { meetupApi } from '@/lib/meetup-api';
import type { AttendanceResult } from '@/types/api/attendance';
import type { MeetupApplicationAdminResponse } from '@/types/api/meetup';

const keyOf = (meetupId: number | null) => ['meetups', 'applications', meetupId];

/** 다시 받는 동안 끝난 동작을 또 누르지 않게 줄을 먼저 고친다. */
function usePatchRow(meetupId: number) {
  const client = useQueryClient();
  return (
    matches: (row: MeetupApplicationAdminResponse) => boolean,
    change: Partial<MeetupApplicationAdminResponse>,
  ) =>
    client.setQueryData<MeetupApplicationAdminResponse[]>(keyOf(meetupId), (old) =>
      old?.map((row) => (matches(row) ? { ...row, ...change } : row)),
    );
}

export function useApplications(meetupId: number | null) {
  const admin = useIsAdmin();
  return useQuery({
    queryKey: keyOf(meetupId),
    queryFn: () => meetupApi.applications(meetupId ?? 0),
    enabled: admin && meetupId !== null,
  });
}

export function useRemoveApplication(meetupId: number) {
  const patch = usePatchRow(meetupId);
  return useAdminMutation(
    // 204라 값이 없으므로 성공을 true로 알린다.
    (id: number) => meetupApi.removeApplication(id).then(() => true),
    [['meetups']],
    {
      success: (_, id) => patch((row) => row.id === id, { status: 'REMOVED' }),
    },
  );
}

export function useAddLateCancelPenalty(meetupId: number) {
  const patch = usePatchRow(meetupId);
  return useAdminMutation(meetupApi.addLateCancelPenalty, [['meetups'], ['penalties']], {
    success: (_, id) => patch((row) => row.id === id, { lateCancelPenalized: true }),
  });
}

export function useCorrectAttendance(meetupId: number) {
  const patch = usePatchRow(meetupId);
  return useAdminMutation(
    ({ id, result }: { id: number; result: AttendanceResult }) => attendanceApi.correct(id, result),
    [['meetups'], ['penalties'], ['attendance-rates']],
    {
      success: (saved, { id }) =>
        patch((row) => row.attendanceId === id, { attendanceResult: saved.result }),
    },
  );
}
