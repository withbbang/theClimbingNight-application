import { addDays, addMonths, type DateKey } from '@/lib/kst';
import type { AttendancePeriod } from '@/types/api/attendance';

/** 앞뒤 기간에 속한 날. 기간의 경계는 서버가 정한다. */
export function shiftPeriod(period: AttendancePeriod, date: DateKey, step: 1 | -1): DateKey {
  switch (period) {
    case 'DAY':
      return addDays(date, step);
    case 'WEEK':
      return addDays(date, 7 * step);
    case 'MONTH':
      return addMonths(date, step);
    case 'QUARTER':
      return addMonths(date, 3 * step);
    case 'YEAR':
      return addMonths(date, 12 * step);
  }
}
