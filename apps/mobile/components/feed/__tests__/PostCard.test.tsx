import { QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { fireGestureHandler, getByGestureTestId } from 'react-native-gesture-handler/jest-utils';

import { PostCard } from '@/components/feed/PostCard';
import { likePost, unlikePost } from '@/lib/post-api';
import { queryClient } from '@/lib/query-client';
import { media, post } from '@/test-utils/posts';
import { startAs } from '@/test-utils/session';
import type { PostResponse } from '@/types/api/post';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush, back: jest.fn() }) }));
jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/post-api', () => ({ likePost: jest.fn(), unlikePost: jest.fn() }));

beforeEach(async () => {
  mockPush.mockClear();
  jest.mocked(likePost).mockReset().mockResolvedValue({ likeCount: 5, likedByMe: true });
  jest.mocked(unlikePost).mockReset().mockResolvedValue({ likeCount: 4, likedByMe: false });
  await startAs('USER');
});

/** 좋아요는 캐시를 고치므로, 카드가 보는 글을 캐시에서 읽어 다시 그린다. */
async function show(
  initial: PostResponse,
  options: { onOpen?: () => void; onCommentPress?: () => void } = {},
) {
  queryClient.setQueryData(['posts', initial.id], initial);
  const { useQuery } =
    jest.requireActual<typeof import('@tanstack/react-query')>('@tanstack/react-query');
  function Connected() {
    const { data } = useQuery({ queryKey: ['posts', initial.id], queryFn: () => initial });
    return (
      <PostCard
        post={data ?? initial}
        onOpen={options.onOpen}
        onCommentPress={options.onCommentPress}
      />
    );
  }
  const view = await render(
    <GestureHandlerRootView>
      <QueryClientProvider client={queryClient}>
        <Connected />
      </QueryClientProvider>
    </GestureHandlerRootView>,
  );
  return view;
}

/** 쿼리 캐시의 변경 알림은 타이머로 전해지고 가짜 타이머가 켜져 있다. */
/** 사진은 폭을 잰 뒤에 그려진다. */
async function layoutPhotos() {
  await fireEvent(screen.getByTestId('media-carousel'), 'layout', {
    nativeEvent: { layout: { width: 360, height: 450 } },
  });
}

async function flush() {
  await act(async () => {
    await jest.advanceTimersByTimeAsync(20);
  });
}

const heart = (liked: boolean) =>
  screen.getByRole('button', { name: liked ? '좋아요 취소' : '좋아요' });

const LONG = '첫 줄\n둘째 줄\n셋째 줄\n넷째 줄';

it('shows the image count dots only when there are two or more', async () => {
  // given
  const view = await show(post({ media: [media(11)] }));
  expect(screen.queryByLabelText(/장 중 \d+번째 사진/)).toBeNull();

  // when
  await view.unmount();
  await show(post({ media: [media(11), media(12)] }));

  // then
  expect(screen.getByLabelText('2장 중 1번째 사진')).toBeOnTheScreen();
});

it('collapses the body after three lines and expands on 더 보기', async () => {
  // given
  await show(post({ body: LONG }));
  expect(screen.getByText(LONG)).toHaveProp('numberOfLines', 3);
  expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();

  // when: 전체 본문이 네 줄로 그려진다.
  const measure = screen.getByTestId('post-body-measure', { includeHiddenElements: true });
  await fireEvent(measure, 'textLayout', {
    nativeEvent: { lines: [{}, {}, {}, {}] },
  });
  await fireEvent.press(screen.getByRole('button', { name: '더 보기' }));

  // then
  expect(screen.getByText(LONG).props.numberOfLines).toBeUndefined();
  expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();
});

it('collapses again when the body changes', async () => {
  // given: 펼쳐 둔 본문
  const view = await show(post({ body: LONG }));
  const measure = screen.getByTestId('post-body-measure', { includeHiddenElements: true });
  await fireEvent(measure, 'textLayout', { nativeEvent: { lines: [{}, {}, {}, {}] } });
  await fireEvent.press(screen.getByRole('button', { name: '더 보기' }));

  // when: 고친 글을 다시 받는다.
  const edited = `${LONG}\n다섯째 줄`;
  await view.unmount();
  await show(post({ body: edited }));

  // then
  expect(screen.getByText(edited)).toHaveProp('numberOfLines', 3);
});

it('toggles the heart and shows the new count', async () => {
  // given
  await show(post({ id: 1, likeCount: 4, likedByMe: false }));
  expect(screen.getByText('4')).toBeOnTheScreen();

  // when
  await fireEvent.press(heart(false));
  await flush();

  // then: 응답 전에 이미 켜지고 수가 늘어난다.
  expect(heart(true)).toBeOnTheScreen();
  expect(screen.getByText('5')).toBeOnTheScreen();
  expect(likePost).toHaveBeenCalledWith(1);

  // when
  await fireEvent.press(heart(true));

  // then
  await flush();
  expect(heart(false)).toBeOnTheScreen();
  expect(screen.getByText('4')).toBeOnTheScreen();
});

it('likes but never unlikes on double tap', async () => {
  // given: 이미 좋아요한 글과 아닌 글
  await show(post({ id: 1, likeCount: 4, likedByMe: false }));

  await layoutPhotos();

  // when
  await fireGestureHandler(getByGestureTestId('photo-double-tap'));
  await flush();
  await fireGestureHandler(getByGestureTestId('photo-double-tap'));
  await flush();

  // then: 두 번 두 번 탭해도 좋아요만 한다.
  expect(likePost).toHaveBeenCalledTimes(1);
  expect(unlikePost).not.toHaveBeenCalled();
  expect(heart(true)).toBeOnTheScreen();
});

it('sends signed-out users to login when the heart is pressed', async () => {
  // given
  await startAs(null);
  await show(post());

  // when
  await fireEvent.press(heart(false));

  // then
  expect(mockPush).toHaveBeenCalledWith('/login');
  expect(likePost).not.toHaveBeenCalled();
});

it('opens the detail at comments from the comment button', async () => {
  // given
  await show(post({ id: 9, commentCount: 2 }));

  // when
  await fireEvent.press(screen.getByRole('button', { name: '댓글 2개' }));

  // then
  expect(mockPush).toHaveBeenCalledWith('/posts/9?focus=comments');
});

it('calls onCommentPress instead of opening the detail', async () => {
  // given
  const onCommentPress = jest.fn();
  await show(post({ id: 9, commentCount: 2 }), { onCommentPress });

  // when
  await fireEvent.press(screen.getByRole('button', { name: '댓글 2개' }));

  // then
  expect(onCommentPress).toHaveBeenCalledTimes(1);
  expect(mockPush).not.toHaveBeenCalled();
});

it('only allows unliking on my hidden post', async () => {
  // given: 숨겨진 내 글, 좋아요는 꺼져 있다.
  const view = await show(post({ mine: true, status: 'HIDDEN', likedByMe: false }));

  // then: 켤 수 없고 두 번 탭도 없다.
  expect(heart(false)).toBeDisabled();
  await fireEvent.press(heart(false));
  expect(likePost).not.toHaveBeenCalled();
  await layoutPhotos();
  await fireGestureHandler(getByGestureTestId('photo-double-tap'));
  await flush();
  expect(likePost).not.toHaveBeenCalled();

  // when: 이미 켜 둔 하트는 끌 수 있다.
  await view.unmount();
  await show(post({ mine: true, status: 'HIDDEN', likedByMe: true, likeCount: 4 }));
  await fireEvent.press(heart(true));
  await flush();

  // then
  expect(unlikePost).toHaveBeenCalledWith(1);
});
