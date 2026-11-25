import { apiFetch } from '@/lib/api';
import type { DateKey } from '@/lib/kst';
import type {
  AttendancePeriod,
  AttendanceRateResponse,
  AttendanceResponse,
  AttendanceResult,
} from '@/types/api/attendance';

export const attendanceApi = {
  myRate(period: AttendancePeriod, date: DateKey) {
    return apiFetch<AttendanceRateResponse>(`/attendance-rates/me?period=${period}&date=${date}`);
  },
  correct(attendanceId: number, result: AttendanceResult) {
    return apiFetch<AttendanceResponse>(`/attendances/${attendanceId}`, {
      method: 'PUT',
      body: { result },
    });
  },
};
