export type AttendancePeriod = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';

export interface AttendanceRateResponse {
  period: AttendancePeriod;
  from: string;
  to: string;
  attendedCount: number;
  meetupCount: number;
  /** 백분율. 참석 기록이 끝난 벙이 없으면 null. */
  rate: number | null;
}

export type AttendanceResult = 'ATTENDED' | 'EXCUSED' | 'NO_SHOW';

export interface AttendanceResponse {
  id: number;
  meetupId: number;
  rosterMemberId: number;
  result: AttendanceResult;
  source: 'AUTO' | 'ADMIN' | 'IMPORT';
}
