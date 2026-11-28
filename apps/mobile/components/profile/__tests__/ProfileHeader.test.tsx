import { QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { ProfileHeader } from '@/components/profile/ProfileHeader';
import { queryClient } from '@/lib/query-client';
import { startAs } from '@/test-utils/session';
import type { ProfileResponse } from '@/types/api/profile';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush, back: jest.fn() }) }));
jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

const profile = (over: Partial<ProfileResponse> = {}): ProfileResponse => ({
  id: 7,
  nickname: '달이',
  bio: '주말 볼더링, 가끔 리드',
  profileImage: null,
  postCount: 24,
  mine: false,
  ...over,
});

async function show(value: ProfileResponse) {
  await startAs('USER');
  await render(
    <QueryClientProvider client={queryClient}>
      <ProfileHeader profile={value} onGone={() => {}} />
    </QueryClientProvider>,
  );
}

beforeEach(() => mockPush.mockClear());

it('shows the nickname, post count and bio', async () => {
  // given / when
  await show(profile());

  // then
  expect(screen.getByText('달이')).toBeOnTheScreen();
  expect(screen.getByText('24')).toBeOnTheScreen();
  expect(screen.getByText('주말 볼더링, 가끔 리드')).toBeOnTheScreen();
});

it('has no bio line when bio is null', async () => {
  // given / when
  await show(profile({ bio: null }));

  // then
  expect(screen.queryByTestId('profile-bio')).toBeNull();
});

it('offers 프로필 편집 on my profile and no menu', async () => {
  // given
  await show(profile({ mine: true }));

  // when
  await fireEvent.press(screen.getByRole('button', { name: '프로필 편집' }));

  // then
  expect(mockPush).toHaveBeenCalledWith('/settings/profile');
  expect(screen.queryByRole('button', { name: '달이 프로필 메뉴' })).toBeNull();
});

it('offers the ⋯ menu with report and block on someone else', async () => {
  // given
  await show(profile());
  expect(screen.queryByRole('button', { name: '프로필 편집' })).toBeNull();

  // when
  await fireEvent.press(screen.getByRole('button', { name: '달이 프로필 메뉴' }));

  // then
  expect(screen.getByRole('button', { name: '신고하기' })).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '차단하기' })).toBeOnTheScreen();
});

it('opens the user report sheet from the menu', async () => {
  // given
  await show(profile());
  await fireEvent.press(screen.getByRole('button', { name: '달이 프로필 메뉴' }));

  // when
  await fireEvent.press(screen.getByRole('button', { name: '신고하기' }));

  // then
  expect(screen.getByText('신고하는 이유를 골라 주세요')).toBeOnTheScreen();
  // 사용자 신고에는 초상권 사유가 있다.
  expect(screen.getByText('초상권·사생활 침해')).toBeOnTheScreen();
});
