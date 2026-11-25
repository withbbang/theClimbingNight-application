import { formatDate } from '@/lib/kst';

it('uses the Seoul date', () => {
  expect(formatDate('2026-12-31T14:59:59Z')).toBe('2026년 12월 31일');
  expect(formatDate('2026-12-31T15:00:00Z')).toBe('2027년 1월 1일');
});

it('reads an offset time', () => {
  expect(formatDate('2026-11-24T00:30:00+09:00')).toBe('2026년 11월 24일');
});
