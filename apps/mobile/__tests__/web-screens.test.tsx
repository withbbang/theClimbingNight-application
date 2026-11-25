import { act, screen } from 'expo-router/testing-library';
import { router } from 'expo-router';

import { mockServer, renderApp } from '@/test-utils/app';
import { button, press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

beforeEach(async () => {
  await startAs(null);
  mockServer({});
});

const page = (path: string) => screen.queryByLabelText(`웹 https://api.test${path}`);

it.each([
  ['/notices', '/app/notices'],
  ['/notices/3', '/app/notices/3'],
  ['/about', '/app/about'],
  ['/legal/terms', '/app/terms'],
  ['/legal/privacy', '/app/privacy'],
])('opens %s as a web page', async (route, path) => {
  await renderApp(route);
  expect(page(path)).toBeOnTheScreen();
  expect(button('뒤로')).toBeOnTheScreen();
});

it('loads the goods page in the tab without a back button', async () => {
  await renderApp('/goods');
  expect(page('/app/goods')).toBeOnTheScreen();
  expect(button('뒤로')).toBeNull();
});

it.each(['abc', '0', '-1'])('shows a way out for the bad notice id %s', async (id) => {
  const app = await renderApp(`/notices/${id}`);
  expect(screen.queryByLabelText(/^웹 /)).toBeNull();
  await press('공지 목록');
  expect(app.getPathname()).toBe('/notices');
});

it('opens another notice in the same place', async () => {
  await renderApp('/notices/3');
  await act(async () => router.setParams({ id: '4' }));
  expect(page('/app/notices/4')).toBeOnTheScreen();
});

it.each([
  ['동호회 소개', '/about'],
  ['이용약관', '/legal/terms'],
  ['개인정보처리방침', '/legal/privacy'],
])('opens %s from my without login', async (label, path) => {
  const app = await renderApp('/my');
  await press(label);
  expect(app.getPathname()).toBe(path);
});
