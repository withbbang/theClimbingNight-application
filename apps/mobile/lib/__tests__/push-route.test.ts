import { routeForPush } from '@/lib/push-route';

it.each([
  ['MEETUP_PROMOTED', '/meetups'],
  ['MEETUP_CANCELED', '/meetups'],
  ['MEETUP_CREATED', '/meetups'],
  ['MEETUP_REMINDER', '/meetups'],
  ['MEMBERSHIP_APPROVED', '/my'],
  ['MEMBERSHIP_REJECTED', '/my'],
  ['INQUIRY_ANSWERED', '/my'],
  ['NOTICE_PUBLISHED', '/'],
])('routes %s to %s', (type, route) => {
  expect(routeForPush({ type })).toBe(route);
});

it.each([null, undefined, 'x', {}, { type: 1 }, { type: 'UNKNOWN' }, { type: 'toString' }])(
  'returns null for %p',
  (data) => {
    expect(routeForPush(data)).toBeNull();
  },
);
