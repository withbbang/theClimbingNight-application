import { fireEvent, screen } from 'expo-router/testing-library';

import type { ProfileImagePhase } from '@/hooks/use-profile-image';
import { flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { button, press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { ProfileResponse } from '@/types/api/profile';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const mockImage = {
  pick: jest.fn(async () => {}),
  reset: jest.fn(async () => {}),
  phase: 'idle' as ProfileImagePhase,
  preview: null as string | null,
  error: null as string | null,
};
jest.mock('@/hooks/use-profile-image', () => ({ useProfileImage: () => mockImage }));

const IMAGE = {
  largeUrl: 'https://media.test/a/1/l.webp',
  smallUrl: 'https://media.test/a/1/s.webp',
};

const mine = (over: Partial<ProfileResponse> = {}): ProfileResponse => ({
  id: 1,
  nickname: '별이',
  bio: '주말 볼더링',
  profileImage: IMAGE,
  postCount: 3,
  mine: true,
  ...over,
});

beforeEach(() => {
  mockImage.pick.mockClear();
  mockImage.reset.mockClear();
  mockImage.phase = 'idle';
  mockImage.preview = null;
  mockImage.error = null;
});

async function open(profile = mine(), extra: Parameters<typeof mockServer>[0] = {}) {
  await startAs('USER');
  const server = mockServer({ '/users/1/profile': ok(profile), ...extra });
  const app = await renderApp('/settings/profile');
  return { app, server };
}

const bioField = () => screen.getByLabelText('자기소개');

it('fills the bio with the current one', async () => {
  // given / when
  await open();

  // then
  expect(bioField().props.value).toBe('주말 볼더링');
});

it('shows how many characters are left', async () => {
  // given
  await open(mine({ bio: null }));

  // when
  await fireEvent.changeText(bioField(), '가'.repeat(150));

  // then
  expect(screen.getByText('0자 남았어요')).toBeOnTheScreen();
  expect(button('저장')).not.toBeDisabled();
});

it('blocks saving at 151 characters and shows the overflow', async () => {
  // given
  await open(mine({ bio: null }));

  // when
  await fireEvent.changeText(bioField(), '가'.repeat(151));

  // then
  expect(screen.getByText('1자 넘었어요')).toBeOnTheScreen();
  expect(button('저장')).toBeDisabled();
});

it('blocks saving at five lines', async () => {
  // given
  await open(mine({ bio: null }));

  // when
  await fireEvent.changeText(bioField(), '하나\n둘\n셋\n넷\n다섯');

  // then
  expect(screen.getByText('자기소개는 150자, 4줄까지 쓸 수 있어요.')).toBeOnTheScreen();
  expect(button('저장')).toBeDisabled();
});

it('saves the trimmed bio and goes back', async () => {
  // given
  const { app, server } = await open(mine({ bio: null }), {
    'PATCH /users/me/profile': ok(mine({ bio: '리드 위주' })),
  });

  // when
  await fireEvent.changeText(bioField(), '  리드 위주  ');
  await press('저장');
  await flush();

  // then
  expect(server.calls).toContainEqual({
    method: 'PATCH',
    path: '/users/me/profile',
    body: { bio: '리드 위주' },
  });
  expect(app.getPathname()).not.toBe('/settings/profile');
});

it('offers both photo actions when I have a photo', async () => {
  // given
  await open();

  // when
  await press('프로필 사진 바꾸기');

  // then
  expect(button('사진 바꾸기')).toBeOnTheScreen();
  expect(button('기본 이미지로')).toBeOnTheScreen();
});

it('offers only 사진 바꾸기 when I have no photo', async () => {
  // given
  await open(mine({ profileImage: null }));

  // when
  await press('프로필 사진 바꾸기');

  // then
  expect(button('사진 바꾸기')).toBeOnTheScreen();
  expect(button('기본 이미지로')).toBeNull();
});

it('picks a photo from the menu', async () => {
  // given
  await open();
  await press('프로필 사진 바꾸기');

  // when
  await press('사진 바꾸기');

  // then
  expect(mockImage.pick).toHaveBeenCalledTimes(1);
});

it('resets to the default image from the menu', async () => {
  // given
  await open();
  await press('프로필 사진 바꾸기');

  // when
  await press('기본 이미지로');

  // then
  expect(mockImage.reset).toHaveBeenCalledTimes(1);
});

it.each([
  ['applying', '적용하는 중'],
  ['slow', '잠시 뒤 반영돼요'],
  ['failed', '사진을 적용하지 못했어요. 다시 올려 주세요'],
] as const)('shows the %s message', async (phase, message) => {
  // given
  mockImage.phase = phase;

  // when
  await open();

  // then
  expect(screen.getByText(message)).toBeOnTheScreen();
});

it.each(['uploading', 'applying'] as const)('locks the photo actions while %s', async (phase) => {
  // given
  mockImage.phase = phase;
  await open();

  // when
  await press('프로필 사진 바꾸기');

  // then
  expect(button('사진 바꾸기')).toBeDisabled();
  expect(button('기본 이미지로')).toBeDisabled();
});

it('opens the nickname screen', async () => {
  // given
  const { app } = await open();

  // when
  await press('닉네임 별이');

  // then
  expect(app.getPathname()).toBe('/settings/nickname');
});
