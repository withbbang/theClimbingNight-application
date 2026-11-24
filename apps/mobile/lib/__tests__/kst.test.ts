import {
  addDays,
  addMonths,
  formatDateTime,
  formatDay,
  formatTime,
  monthGrid,
  toDateKey,
  todayKst,
} from '@/lib/kst';

it('uses the Seoul date, not the device date', () => {
  expect(todayKst(new Date('2026-11-24T14:59:59Z'))).toBe('2026-11-24');
  expect(todayKst(new Date('2026-11-24T15:00:00Z'))).toBe('2026-11-25');
});

it('formats in Seoul time', () => {
  jest.useFakeTimers({ now: new Date('2026-11-24T03:00:00Z') });
  expect(formatDateTime('2026-11-24T10:30:00Z')).toBe('11월 24일(화) 19:30');
  expect(formatTime('2026-11-23T15:05:00Z')).toBe('00:05');
  expect(formatDay('2026-11-01')).toBe('11월 1일(일)');
  expect(toDateKey('2026-12-31T15:00:00Z')).toBe('2027-01-01');
  jest.useRealTimers();
});

it('clamps month ends', () => {
  expect(addMonths('2027-01-31', 1)).toBe('2027-02-28');
  expect(addMonths('2028-02-29', 12)).toBe('2029-02-28');
  expect(addMonths('2027-01-15', -1)).toBe('2026-12-15');
  expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
});

it('builds full weeks from Sunday', () => {
  const grid = monthGrid('2026-11-24');
  expect(grid[0][0]).toBe('2026-11-01');
  expect(grid.at(-1)?.at(-1)).toBe('2026-12-05');
  expect(grid.every((week) => week.length === 7)).toBe(true);
});

it('adds no extra week when the month ends on Saturday', () => {
  expect(monthGrid('2026-10-05').at(-1)?.at(-1)).toBe('2026-10-31');
});

it('adds the year to dates of earlier years only', () => {
  expect(formatDay('2025-12-31', '2026-01-01')).toBe('2025년 12월 31일(수)');
  expect(formatDay('2026-01-01', '2026-01-01')).toBe('1월 1일(목)');
  expect(formatDay('2027-01-01', '2026-12-31')).toBe('1월 1일(금)');
});

it('decides the year by the Seoul date', () => {
  jest.useFakeTimers({ now: new Date('2026-12-31T15:00:00Z') });
  expect(formatDateTime('2026-12-31T14:59:00Z')).toBe('2026년 12월 31일(목) 23:59');
  expect(formatDateTime('2026-12-31T15:00:00Z')).toBe('1월 1일(금) 00:00');
  jest.useRealTimers();
});
