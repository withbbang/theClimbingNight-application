import { createElement, type ReactNode } from 'react';

import { act, renderHook } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { launchImageLibraryAsync } from 'expo-image-picker';

import { useProfileImage } from '@/hooks/use-profile-image';
import { ApiError } from '@/lib/api-error';
import { notifyError } from '@/lib/haptics';
import { profileApi } from '@/lib/profile-api';
import { sessionStore } from '@/lib/session-store';
import { prepareProfilePhoto, uploadProfilePhoto } from '@/lib/upload-profile-image';
import { tokens } from '@/test-utils/tokens';
import type { ProfileImageStatus } from '@/types/api/profile';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-image-picker', () => ({ launchImageLibraryAsync: jest.fn() }));
jest.mock('@/lib/haptics', () => ({ notifyError: jest.fn(), notifySuccess: jest.fn() }));
jest.mock('@/lib/upload-profile-image', () => ({
  prepareProfilePhoto: jest.fn(),
  uploadProfilePhoto: jest.fn(),
}));
jest.mock('@/lib/profile-api', () => ({
  profileApi: {
    startImage: jest.fn(),
    confirmImage: jest.fn(),
    imageStatus: jest.fn(),
    resetImage: jest.fn(),
  },
}));

let client: QueryClient;
let invalidate: jest.SpyInstance;

const status = (value: ProfileImageStatus) => ({ id: 9, status: value });

async function setup() {
  jest.useFakeTimers();
  await sessionStore.signOut();
  await sessionStore.signIn(tokens('1', 'MEMBER'));
  client = new QueryClient();
  invalidate = jest.spyOn(client, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return renderHook(() => useProfileImage(), { wrapper });
}

beforeEach(() => {
  jest.resetAllMocks();
  jest.mocked(launchImageLibraryAsync).mockResolvedValue({
    canceled: false,
    assets: [{ uri: 'file:///picked.jpg', width: 800, height: 800 }],
  });
  jest.mocked(prepareProfilePhoto).mockResolvedValue({
    uri: 'file:///cache/p.jpg',
    width: 800,
    height: 800,
    byteSize: 1234,
    contentType: 'image/jpeg',
  });
  jest.mocked(uploadProfilePhoto).mockResolvedValue(undefined);
  jest.mocked(profileApi.startImage).mockResolvedValue({
    id: 9,
    url: 'https://r2.test/x',
    headers: {},
    expiresAt: '2026-11-26T03:15:00Z',
  });
  jest.mocked(profileApi.confirmImage).mockResolvedValue(status('PROCESSING'));
});

afterEach(() => {
  client.clear();
  jest.useRealTimers();
});

/** pick은 폴링이 끝나야 끝나므로 기다리지 않고, 확정까지의 비동기 단계만 흘려보낸다. */
async function startPick(result: { current: ReturnType<typeof useProfileImage> }) {
  await act(async () => {
    void result.current.pick();
    await jest.advanceTimersByTimeAsync(0);
  });
}

const tick = (ms: number) =>
  act(async () => {
    await jest.advanceTimersByTimeAsync(ms);
  });

it('invalidates my profile and goes idle once the status turns READY', async () => {
  // given
  jest.mocked(profileApi.imageStatus).mockResolvedValueOnce(status('PROCESSING'));
  jest.mocked(profileApi.imageStatus).mockResolvedValue(status('READY'));
  const { result } = await setup();

  // when
  await startPick(result);
  await tick(2000);
  await tick(2000);

  // then
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 1, 'profile'] });
  expect(result.current.phase).toBe('idle');
  expect(result.current.preview).toBeNull();
});

it('sets phase failed when the status is FAILED', async () => {
  jest.mocked(profileApi.imageStatus).mockResolvedValue(status('FAILED'));
  const { result } = await setup();

  await startPick(result);
  await tick(2000);

  expect(result.current.phase).toBe('failed');
  expect(invalidate).not.toHaveBeenCalled();
});

it('goes slow after 30 seconds of PROCESSING and stops asking', async () => {
  // given
  jest.mocked(profileApi.imageStatus).mockResolvedValue(status('PROCESSING'));
  const { result } = await setup();

  // when
  await startPick(result);
  await tick(30_000);

  // then
  expect(result.current.phase).toBe('slow');
  const calls = jest.mocked(profileApi.imageStatus).mock.calls.length;
  expect(calls).toBeLessThanOrEqual(16);
  await tick(60_000);
  expect(jest.mocked(profileApi.imageStatus).mock.calls.length).toBe(calls);
});

it('quietly invalidates and goes idle when the status is REMOVED', async () => {
  jest.mocked(profileApi.imageStatus).mockResolvedValue(status('REMOVED'));
  const { result } = await setup();

  await startPick(result);
  await tick(2000);

  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 1, 'profile'] });
  expect(result.current.phase).toBe('idle');
  expect(notifyError).not.toHaveBeenCalled();
});

it('notifies the message when PROFILE_IMAGE_BUSY comes back', async () => {
  // given
  const message = '앞서 올린 사진을 적용하는 중이에요. 잠시 뒤 다시 해 주세요.';
  jest
    .mocked(profileApi.startImage)
    .mockRejectedValue(new ApiError('PROFILE_IMAGE_BUSY', message, 409));
  const { result } = await setup();

  // when
  await startPick(result);

  // then
  expect(notifyError).toHaveBeenCalled();
  expect(result.current.error).toBe(message);
  expect(result.current.phase).toBe('idle');
  expect(uploadProfilePhoto).not.toHaveBeenCalled();
});

it('does nothing when the picker is cancelled', async () => {
  jest.mocked(launchImageLibraryAsync).mockResolvedValue({ canceled: true, assets: null });
  const { result } = await setup();

  await startPick(result);

  expect(profileApi.startImage).not.toHaveBeenCalled();
  expect(result.current.phase).toBe('idle');
});

it('stops asking when the screen unmounts', async () => {
  jest.mocked(profileApi.imageStatus).mockResolvedValue(status('PROCESSING'));
  const { result, unmount } = await setup();
  await startPick(result);
  await tick(2000);
  const calls = jest.mocked(profileApi.imageStatus).mock.calls.length;

  await act(async () => unmount());
  await tick(10_000);

  expect(jest.mocked(profileApi.imageStatus).mock.calls.length).toBe(calls);
});

it('resets to the default image and invalidates my profile', async () => {
  jest.mocked(profileApi.resetImage).mockResolvedValue(undefined);
  const { result } = await setup();

  await act(async () => {
    await result.current.reset();
  });

  expect(profileApi.resetImage).toHaveBeenCalledTimes(1);
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 1, 'profile'] });
});

it('treats a failed status request while waiting like slow and refreshes my profile', async () => {
  // given
  jest.mocked(profileApi.imageStatus).mockRejectedValue(new TypeError('Network request failed'));
  const { result } = await setup();

  // when
  await startPick(result);
  await tick(2000);

  // then
  expect(result.current.phase).toBe('slow');
  expect(result.current.error).toBeNull();
  expect(notifyError).not.toHaveBeenCalled();
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 1, 'profile'] });
  const calls = jest.mocked(profileApi.imageStatus).mock.calls.length;
  await tick(10_000);
  expect(jest.mocked(profileApi.imageStatus).mock.calls.length).toBe(calls);
});
