import { useQuery } from '@tanstack/react-query';

import { useIsMember } from '@/hooks/use-role';
import { attendanceApi } from '@/lib/attendance-api';
import type { DateKey } from '@/lib/kst';
import type { AttendancePeriod } from '@/types/api/attendance';

export function useAttendanceRate(period: AttendancePeriod, date: DateKey) {
  return useQuery({
    queryKey: ['attendance-rates', period, date],
    queryFn: () => attendanceApi.myRate(period, date),
    enabled: useIsMember(),
  });
}
