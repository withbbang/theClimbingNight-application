import { createElement, type ReactNode } from 'react';

import { act, renderHook } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { useCreatePost } from '@/hooks/use-create-post';
import { createDraft, publishPost } from '@/lib/post-api';
import { uploadPhotos, type PreparedPhoto } from '@/lib/upload-photos';
import { post } from '@/test-utils/posts';

jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/post-api', () => ({ createDraft: jest.fn(), publishPost: jest.fn() }));
jest.mock('@/lib/upload-photos', () => ({ uploadPhotos: jest.fn() }));

const draft = {
  postId: 31,
  uploads: [1, 2].map((n) => ({
    mediaId: n,
    url: `https://r2.test/${n}`,
    headers: { 'Content-Type': 'image/jpeg', 'Content-Length': '3' },
    expiresAt: '2026-11-26T03:15:00Z',
  })),
};

const photos: PreparedPhoto[] = [1, 2].map((n) => ({
  uri: `file:///cache/${n}.jpg`,
  width: 2048,
  height: 1536 + n,
  byteSize: 1000 + n,
  contentType: 'image/jpeg',
}));

let client: QueryClient;
afterEach(() => client.clear());

async function setup() {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = jest.spyOn(client, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  const hook = await renderHook(() => useCreatePost(), { wrapper });
  return { ...hook, invalidate };
}

beforeEach(() => {
  jest.mocked(createDraft).mockReset().mockResolvedValue(draft);
  jest
    .mocked(publishPost)
    .mockReset()
    .mockResolvedValue(post({ id: 31, status: 'PENDING' }));
  jest.mocked(uploadPhotos).mockReset();
});

it('publishes after every upload succeeds', async () => {
  // given
  const order: string[] = [];
  jest.mocked(uploadPhotos).mockImplementation(async (_uploads, _photos, onProgress) => {
    order.push('upload');
    onProgress(1, 2);
    onProgress(2, 2);
  });
  jest.mocked(publishPost).mockImplementation(async () => {
    order.push('publish');
    return post({ id: 31, status: 'PENDING' });
  });
  const { result, invalidate } = await setup();

  // when
  await act(async () => {
    await result.current.submit(photos, '오늘 초록 완등');
  });

  // then: 사진의 크기 정보로 초안을 만들고, 모두 올린 뒤에 게시한다.
  expect(createDraft).toHaveBeenCalledWith([
    { contentType: 'image/jpeg', byteSize: 1001, width: 2048, height: 1537 },
    { contentType: 'image/jpeg', byteSize: 1002, width: 2048, height: 1538 },
  ]);
  expect(uploadPhotos).toHaveBeenCalledWith(draft.uploads, photos, expect.any(Function));
  expect(order).toEqual(['upload', 'publish']);
  expect(publishPost).toHaveBeenCalledWith(31, '오늘 초록 완등');
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['posts', 'mine', 'in-progress'] });
  expect(result.current.error).toBeNull();
});

it('does not publish when an upload fails', async () => {
  // given
  jest.mocked(uploadPhotos).mockRejectedValue(new Error('사진을 올리지 못했어요.'));
  const { result } = await setup();

  // when
  await act(async () => {
    await result.current.submit(photos, '');
  });

  // then
  expect(publishPost).not.toHaveBeenCalled();
  expect(result.current.error?.message).toBe('사진을 올리지 못했어요.');
  expect(result.current.progress).toBeNull();
});

it('sends once when submit is pressed twice in a row', async () => {
  // given
  jest.mocked(uploadPhotos).mockResolvedValue();
  const { result } = await setup();

  // when
  await act(async () => {
    await Promise.all([result.current.submit(photos, ''), result.current.submit(photos, '')]);
  });

  // then
  expect(createDraft).toHaveBeenCalledTimes(1);
});

it('shows how many photos are up while uploading', async () => {
  // given: 첫 사진을 올린 뒤에 멈춰 있다.
  let finish = () => {};
  jest.mocked(uploadPhotos).mockImplementation(
    (_uploads, _photos, onProgress) =>
      new Promise<void>((resolve) => {
        onProgress(1, 2);
        finish = resolve;
      }),
  );
  const { result } = await setup();

  // when
  let sending: Promise<unknown> = Promise.resolve();
  await act(async () => {
    sending = result.current.submit(photos, '');
  });

  // then
  expect(result.current.progress).toEqual({ done: 1, total: 2 });
  await act(async () => {
    finish();
    await sending;
  });
  expect(result.current.progress).toBeNull();
});
