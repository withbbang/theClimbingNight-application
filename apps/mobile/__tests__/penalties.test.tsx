import { screen } from 'expo-router/testing-library';

import { mockServer, ok, renderApp } from '@/test-utils/app';
import { startAs } from '@/test-utils/session';
import type { PenaltyResponse } from '@/types/api/penalty';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const penalty = (over: Partial<PenaltyResponse>): PenaltyResponse => ({
  id: 1,
  meetupId: 7,
  meetupTitle: '화요 정기벙',
  meetupStartsAt: '2026-11-10T10:30:00Z',
  type: 'LATE_CANCEL',
  active: true,
  revokeReason: null,
  ...over,
});

beforeEach(() => startAs('MEMBER'));

it('counts active records and shows revoked ones with the reason', async () => {
  mockServer({
    '/penalties/me': ok([
      penalty({}),
      penalty({ id: 2, type: 'NO_SHOW', active: false, revokeReason: '부상 확인' }),
    ]),
  });
  await renderApp('/penalties');
  expect(screen.getByText('1건')).toBeOnTheScreen();
  expect(screen.getByText('늦은 취소')).toBeOnTheScreen();
  expect(screen.getByText('노쇼')).toBeOnTheScreen();
  expect(screen.getByText('취소됨: 부상 확인')).toBeOnTheScreen();
  expect(screen.getAllByText('11월 10일(화) 19:30')).toHaveLength(2);
});

it('shows an empty notice without penalties', async () => {
  mockServer({ '/penalties/me': ok([]) });
  await renderApp('/penalties');
  expect(screen.getByText('기록이 없어요')).toBeOnTheScreen();
});

it('does not ask for a USER', async () => {
  await startAs('USER');
  const server = mockServer({});
  await renderApp('/penalties');
  expect(server.count('/penalties/me')).toBe(0);
});
