import { capacityText, meetupPhase } from '@/lib/meetup-status';

const meetup = {
  startsAt: '2026-11-24T10:00:00Z',
  endsAt: '2026-11-24T12:00:00Z',
  status: 'OPEN' as const,
};

it.each([
  ['2026-11-24T09:59:59.999Z', 'UPCOMING'],
  ['2026-11-24T10:00:00Z', 'ONGOING'],
  ['2026-11-24T11:59:59.999Z', 'ONGOING'],
  ['2026-11-24T12:00:00Z', 'ENDED'],
])('at %s the phase is %s', (now, phase) => {
  expect(meetupPhase(meetup, new Date(now))).toBe(phase);
});

it('ends at the start when the end is unknown', () => {
  expect(meetupPhase({ ...meetup, endsAt: null }, new Date('2026-11-24T10:00:00Z'))).toBe('ENDED');
});

it('is canceled whatever the time', () => {
  expect(meetupPhase({ ...meetup, status: 'CANCELED' }, new Date(0))).toBe('CANCELED');
});

it('writes counts with and without a capacity', () => {
  expect(capacityText(8, 12)).toBe('확정 8/12');
  expect(capacityText(8, null)).toBe('확정 8');
});
