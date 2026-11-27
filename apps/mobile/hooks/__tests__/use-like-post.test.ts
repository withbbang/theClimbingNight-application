import { createElement, type ReactNode } from 'react';

import { act, renderHook } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, type InfiniteData } from '@tanstack/react-query';

import { useLikePost } from '@/hooks/use-like-post';
import { ApiError } from '@/lib/api-error';
import { likePost, unlikePost } from '@/lib/post-api';
import { post } from '@/test-utils/posts';
import type { CursorResponse } from '@/types/api/common';
import type { LikeResponse, PostResponse } from '@/types/api/post';

jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/post-api', () => ({ likePost: jest.fn(), unlikePost: jest.fn() }));

type Feed = InfiniteData<CursorResponse<PostResponse>, string | null>;

const target = post({ id: 5, likeCount: 4, likedByMe: false });
const other = post({ id: 6, likeCount: 9, likedByMe: false });

let client: QueryClient;
afterEach(() => client.clear());

async function setup() {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const feed: Feed = {
    pages: [{ items: [target, other], nextCursor: null }],
    pageParams: [null],
  };
  client.setQueryData(['posts'], feed);
  client.setQueryData(['posts', 5], target);
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return renderHook(() => useLikePost(), { wrapper });
}

const inFeed = (id: number) =>
  client.getQueryData<Feed>(['posts'])?.pages[0].items.find((item) => item.id === id);
const detail = () => client.getQueryData<PostResponse>(['posts', 5]);

function deferred() {
  let resolve: (value: LikeResponse) => void = () => {};
  let reject: (error: unknown) => void = () => {};
  const promise = new Promise<LikeResponse>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  jest.mocked(likePost).mockReset();
  jest.mocked(unlikePost).mockReset();
});

it('updates feed and detail caches before the request resolves', async () => {
  // given: 응답이 오지 않는 요청
  jest.mocked(likePost).mockReturnValue(deferred().promise);
  const { result } = await setup();

  // when
  await act(async () => {
    result.current.setLiked(target, true);
  });

  // then: 두 캐시가 바로 바뀌고 다른 글은 그대로다.
  expect(inFeed(5)).toMatchObject({ likedByMe: true, likeCount: 5 });
  expect(detail()).toMatchObject({ likedByMe: true, likeCount: 5 });
  expect(inFeed(6)).toMatchObject({ likedByMe: false, likeCount: 9 });
});

it('settles on the like count the server returns', async () => {
  // given: 그 사이 다른 사람도 눌러 서버 수가 더 크다.
  jest.mocked(likePost).mockResolvedValue({ likeCount: 8, likedByMe: true });
  const { result } = await setup();

  // when
  await act(async () => {
    result.current.setLiked(target, true);
  });

  // then
  expect(inFeed(5)).toMatchObject({ likedByMe: true, likeCount: 8 });
  expect(detail()).toMatchObject({ likedByMe: true, likeCount: 8 });
});

it('rolls back when the request fails', async () => {
  // given
  const request = deferred();
  jest.mocked(likePost).mockReturnValue(request.promise);
  const { result } = await setup();
  await act(async () => {
    result.current.setLiked(target, true);
  });

  // when
  await act(async () => {
    request.reject(new ApiError('COMMON_INTERNAL', '실패', 500));
  });

  // then
  expect(inFeed(5)).toMatchObject({ likedByMe: false, likeCount: 4 });
  expect(detail()).toMatchObject({ likedByMe: false, likeCount: 4 });
});

it('sends only the final state after rapid toggles', async () => {
  // given: 첫 요청이 끝나기 전에 연타한다.
  const first = deferred();
  jest.mocked(likePost).mockReturnValue(first.promise);
  jest.mocked(unlikePost).mockResolvedValue({ likeCount: 4, likedByMe: false });
  const { result } = await setup();

  // when: 켜고 끄고 켜고 끈다.
  await act(async () => {
    result.current.setLiked(target, true);
    result.current.setLiked(target, false);
    result.current.setLiked(target, true);
    result.current.setLiked(target, false);
  });
  expect(likePost).toHaveBeenCalledTimes(1);
  await act(async () => {
    first.resolve({ likeCount: 5, likedByMe: true });
  });

  // then: 중간 상태는 보내지 않고, 첫 요청 뒤 최종 상태만 한 번 더 보낸다.
  expect(likePost).toHaveBeenCalledTimes(1);
  expect(unlikePost).toHaveBeenCalledTimes(1);
  expect(inFeed(5)).toMatchObject({ likedByMe: false, likeCount: 4 });
});

it('does not send anything when the post is already in the wanted state', async () => {
  // given
  const { result } = await setup();

  // when
  await act(async () => {
    result.current.setLiked(target, false);
  });

  // then
  expect(unlikePost).not.toHaveBeenCalled();
});

it('leaves other lists under the post key alone even when an item id matches', async () => {
  // given: 댓글 id가 글 id와 같다.
  jest.mocked(likePost).mockReturnValue(deferred().promise);
  const { result } = await setup();
  const comment = { id: 5, body: '댓글' };
  client.setQueryData(['posts', 5, 'comments'], { pages: [{ items: [comment] }], pageParams: [null] });
  client.setQueryData(['posts', 5, 'comments', 3], { pages: [{ items: [comment] }], pageParams: [null] });
  client.setQueryData(['posts', 5, 'likes'], { pages: [{ items: [{ id: 5 }] }], pageParams: [null] });

  // when
  await act(async () => {
    result.current.setLiked(target, true);
  });

  // then
  const items = (key: unknown[]) =>
    client.getQueryData<{ pages: { items: unknown[] }[] }>(key)?.pages[0].items;
  expect(items(['posts', 5, 'comments'])).toEqual([comment]);
  expect(items(['posts', 5, 'comments', 3])).toEqual([comment]);
  expect(items(['posts', 5, 'likes'])).toEqual([{ id: 5 }]);
  expect(inFeed(5)).toMatchObject({ likedByMe: true });
});
