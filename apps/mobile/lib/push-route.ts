import type { Href } from 'expo-router';

const ROUTES = new Map<unknown, Href>([
  ['MEETUP_PROMOTED', '/meetups'],
  ['MEETUP_CANCELED', '/meetups'],
  ['MEETUP_CREATED', '/meetups'],
  ['MEETUP_REMINDER', '/meetups'],
  ['MEMBERSHIP_APPROVED', '/my'],
  ['MEMBERSHIP_REJECTED', '/my'],
  ['INQUIRY_ANSWERED', '/my'],
  ['NOTICE_PUBLISHED', '/'],
]);

function typeOf(data: unknown) {
  return typeof data === 'object' && data !== null && 'type' in data ? data.type : undefined;
}

export function routeForPush(data: unknown): Href | null {
  return ROUTES.get(typeOf(data)) ?? null;
}

/** 내 등급이 바뀌었다는 푸시인가. */
export function isMembershipPush(data: unknown) {
  const type = typeOf(data);
  return typeof type === 'string' && type.startsWith('MEMBERSHIP_');
}
