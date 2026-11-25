import { screen } from 'expo-router/testing-library';

import { mockServer, renderApp } from '@/test-utils/app';
import { press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

it.each([
  ['벙 열기', '/admin/meetups/new'],
  ['인증 심사', '/admin/requests'],
  ['회원 목록', '/admin/members'],
  ['블랙리스트', '/admin/penalties'],
  ['공지 관리', '/admin/notices'],
  ['문의 답변', '/admin/inquiries'],
])('opens %s', async (label, path) => {
  await startAs('ADMIN');
  mockServer({});
  const app = await renderApp('/my');
  await press('운영진 메뉴');
  expect(app.getPathname()).toBe('/admin');
  await press(label);
  expect(app.getPathname()).toBe(path);
});

it('blocks a MEMBER who opens an admin link', async () => {
  await startAs('MEMBER');
  mockServer({});
  await renderApp('/admin');
  expect(screen.getByText('운영진만 볼 수 있어요')).toBeOnTheScreen();
});
