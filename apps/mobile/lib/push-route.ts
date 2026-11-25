import type { QueryKey } from '@tanstack/react-query';
import type { Href } from 'expo-router';

import { parseId } from '@/lib/parse-id';

interface PushRule {
  route: (data: object) => Href;
  keys: QueryKey[];
}

function idOf(data: object, key: string) {
  return parseId((data as Record<string, unknown>)[key]);
}

const meetup: PushRule = {
  route: (data) => {
    const id = idOf(data, 'meetupId');
    return id === null ? '/meetups' : `/meetups/${id}`;
  },
  keys: [['meetups']],
};
const membership = (route: Href): PushRule => ({
  route: () => route,
  keys: [['users', 'me'], ['membership-requests']],
});

const RULES = new Map<unknown, PushRule>([
  ['MEETUP_PROMOTED', meetup],
  ['MEETUP_CANCELED', meetup],
  ['MEETUP_CREATED', meetup],
  ['MEETUP_REMINDER', meetup],
  ['MEMBERSHIP_APPROVED', membership('/meetups')],
  ['MEMBERSHIP_REJECTED', membership('/membership')],
  [
    'INQUIRY_ANSWERED',
    {
      route: (data) => {
        const id = idOf(data, 'inquiryId');
        return id === null ? '/inquiries' : `/inquiries/${id}`;
      },
      keys: [['inquiries']],
    },
  ],
  [
    'NOTICE_PUBLISHED',
    {
      route: (data) => {
        const id = idOf(data, 'noticeId');
        return id === null ? '/notices' : `/notices/${id}`;
      },
      keys: [['notices']],
    },
  ],
]);

function ruleOf(data: unknown) {
  return typeof data === 'object' && data !== null && 'type' in data
    ? { data, rule: RULES.get(data.type) }
    : null;
}

export function routeForPush(data: unknown): Href | null {
  const found = ruleOf(data);
  return found?.rule?.route(found.data) ?? null;
}

/** 푸시가 알린 변화로 낡은 조회들. */
export function queryKeysForPush(data: unknown): QueryKey[] {
  return ruleOf(data)?.rule?.keys ?? [];
}
