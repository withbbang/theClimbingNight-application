import { mockServer, renderApp } from '@/test-utils/app';
import { button, press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

it.each([
  ['USER', false, true],
  ['MEMBER', true, false],
  ['ADMIN', true, false],
] as const)('shows the menus for %s', async (role, member, verification) => {
  await startAs(role);
  mockServer({});
  await renderApp('/my');
  expect(button('내 참석률') !== null).toBe(member);
  expect(button('내 블랙리스트 기록') !== null).toBe(member);
  expect(button('동호회원 인증') !== null).toBe(verification);
  expect(button('운영진 메뉴') !== null).toBe(role === 'ADMIN');
});

it.each([
  ['내 참석률', '/attendance'],
  ['내 블랙리스트 기록', '/penalties'],
  ['문의', '/inquiries'],
  ['알림 설정', '/settings/notifications'],
  ['닉네임 변경', '/settings/nickname'],
  ['동호회 소개', '/about'],
  ['이용약관', '/legal/terms'],
  ['개인정보처리방침', '/legal/privacy'],
  ['탈퇴', '/settings/withdraw'],
])('opens %s', async (label, path) => {
  await startAs('MEMBER');
  mockServer({});
  const app = await renderApp('/my');
  await press(label);
  expect(app.getPathname()).toBe(path);
});

it('opens my profile from the top area', async () => {
  // given
  await startAs('MEMBER');
  mockServer({});
  const app = await renderApp('/my');

  // when
  await press('내 프로필 보기');

  // then
  expect(app.getPathname()).toBe('/users/1');
});
