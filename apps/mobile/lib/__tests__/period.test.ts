import { shiftPeriod } from '@/lib/period';

it.each([
  ['DAY', '2026-12-31', 1, '2027-01-01'],
  ['WEEK', '2026-12-28', 1, '2027-01-04'],
  ['MONTH', '2026-01-31', 1, '2026-02-28'],
  ['QUARTER', '2026-11-30', 1, '2027-02-28'],
  ['YEAR', '2028-02-29', 1, '2029-02-28'],
  ['YEAR', '2026-11-24', -1, '2025-11-24'],
] as const)('shifts %s from %s by %s to %s', (period, date, step, expected) => {
  expect(shiftPeriod(period, date, step)).toBe(expected);
});
