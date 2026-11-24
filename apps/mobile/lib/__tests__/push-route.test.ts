import { queryKeysForPush, routeForPush } from '@/lib/push-route';

it.each([
  [{ type: 'MEETUP_PROMOTED', meetupId: 7 }, '/meetups/7'],
  [{ type: 'MEETUP_CANCELED', meetupId: 7 }, '/meetups/7'],
  [{ type: 'MEETUP_CREATED', meetupId: '7' }, '/meetups/7'],
  [{ type: 'MEETUP_REMINDER', meetupId: 7 }, '/meetups/7'],
  [{ type: 'MEETUP_REMINDER' }, '/meetups'],
  [{ type: 'MEETUP_REMINDER', meetupId: '../my' }, '/meetups'],
  [{ type: 'MEMBERSHIP_APPROVED' }, '/meetups'],
  [{ type: 'MEMBERSHIP_REJECTED' }, '/membership'],
  [{ type: 'INQUIRY_ANSWERED', inquiryId: 3 }, '/inquiries/3'],
  [{ type: 'INQUIRY_ANSWERED', inquiryId: 'x' }, '/inquiries'],
  [{ type: 'NOTICE_PUBLISHED', noticeId: 2 }, '/'],
])('routes %p to %s', (data, route) => {
  expect(routeForPush(data)).toBe(route);
});

it.each([
  ['MEETUP_CREATED', [['meetups']]],
  ['MEMBERSHIP_APPROVED', [['users', 'me'], ['membership-requests']]],
  ['MEMBERSHIP_REJECTED', [['users', 'me'], ['membership-requests']]],
  ['INQUIRY_ANSWERED', [['inquiries']]],
  ['NOTICE_PUBLISHED', [['notices']]],
])('refetches for %s', (type, keys) => {
  expect(queryKeysForPush({ type })).toEqual(keys);
});

it.each([null, undefined, 'x', {}, { type: 1 }, { type: 'UNKNOWN' }, { type: 'toString' }])(
  'ignores %p',
  (data) => {
    expect(routeForPush(data)).toBeNull();
    expect(queryKeysForPush(data)).toEqual([]);
  },
);
