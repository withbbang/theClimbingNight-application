import {
  scheduleToIso,
  toSchedule,
  validateMeetup,
  type MeetupFormValues,
} from '@/lib/meetup-form';

const values = (over: Partial<MeetupFormValues> = {}): MeetupFormValues => ({
  title: ' 화요 정기벙 ',
  placeName: ' 더클라임 ',
  description: ' ',
  day: '2026-11-25',
  start: '1930',
  end: '2230',
  capacity: '12',
  lateCancelHours: '24',
  participantsVisible: true,
  ...over,
});

it('ends the next day when the end is not after the start', () => {
  expect(scheduleToIso({ day: '2026-11-25', start: '2200', end: '0100' })).toEqual({
    startsAt: '2026-11-25T13:00:00.000Z',
    endsAt: '2026-11-25T16:00:00.000Z',
  });
  expect(scheduleToIso({ day: '2026-11-25', start: '1900', end: '1900' })?.endsAt).toBe(
    '2026-11-26T10:00:00.000Z',
  );
  expect(scheduleToIso({ day: '2026-11-25', start: '2500', end: '1900' })).toBeNull();
});

it.each([
  ['2026-11-25T10:30:00.000Z', '2026-11-25T13:30:00.000Z'],
  ['2026-11-25T14:30:00.000Z', '2026-11-25T16:00:00.000Z'],
  ['2026-11-25T10:30:00.000Z', '2026-11-26T10:30:00.000Z'],
])('round-trips %s ~ %s', (startsAt, endsAt) => {
  expect(scheduleToIso(toSchedule(startsAt, endsAt))).toEqual({ startsAt, endsAt });
});

it('trims the texts and sends a blank description as null', () => {
  expect(validateMeetup(values())).toEqual({
    errors: {},
    body: {
      title: '화요 정기벙',
      placeName: '더클라임',
      description: null,
      startsAt: '2026-11-25T10:30:00.000Z',
      endsAt: '2026-11-25T13:30:00.000Z',
      capacity: 12,
      lateCancelHours: 24,
      participantsVisible: true,
    },
  });
});

it.each([
  [{ title: ' ' }, 'title'],
  [{ placeName: '' }, 'placeName'],
  [{ start: '25' }, 'start'],
  [{ end: '' }, 'end'],
  [{ capacity: '0' }, 'capacity'],
  [{ capacity: '501' }, 'capacity'],
  [{ capacity: '1.5' }, 'capacity'],
  [{ capacity: '' }, 'capacity'],
  [{ lateCancelHours: '169' }, 'lateCancelHours'],
  [{ lateCancelHours: '-1' }, 'lateCancelHours'],
] as const)('rejects %p', (over, field) => {
  const result = validateMeetup(values(over));
  expect(result.body).toBeNull();
  expect(Object.keys(result.errors)).toEqual([field]);
});

it.each([
  ['1', '0'],
  ['500', '168'],
])('accepts capacity %s and %s hours', (capacity, lateCancelHours) => {
  expect(validateMeetup(values({ capacity, lateCancelHours })).body).not.toBeNull();
});
