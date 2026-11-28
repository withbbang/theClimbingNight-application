import { StyleSheet, View } from 'react-native';

import { fireEvent, render, screen } from '@testing-library/react-native';

import { ProfileGrid } from '@/components/profile/ProfileGrid';
import type { GridPostResponse } from '@/types/api/profile';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }) }));
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

// 칸 안의 그림은 스크린 리더에서 가려져 있다.
const HIDDEN = { includeHiddenElements: true };

const cell = (id: number, over: Partial<GridPostResponse> = {}): GridPostResponse => ({
  id,
  thumbnailUrl: `https://media.test/p/${id}/t.webp`,
  mediaCount: 1,
  status: null,
  ...over,
});

async function show(posts: GridPostResponse[]) {
  await render(
    <ProfileGrid
      posts={posts}
      header={<View />}
      empty={null}
      onEndReached={() => {}}
      onRefresh={async () => {}}
    />,
  );
}

beforeEach(() => mockPush.mockClear());

it('lays the posts out three to a row', async () => {
  // given / when
  await show([cell(1), cell(2), cell(3), cell(4)]);

  // then
  const rows = screen.getAllByTestId('grid-row');
  expect(rows).toHaveLength(2);
  expect(screen.getAllByRole('button')).toHaveLength(4);
});

it('makes every cell square', async () => {
  // given / when
  await show([cell(1), cell(2)]);

  // then
  for (const button of screen.getAllByRole('button')) {
    expect(StyleSheet.flatten(button.props.style).aspectRatio).toBe(1);
  }
});

it('marks posts with more than one photo', async () => {
  // given / when
  await show([cell(1, { mediaCount: 3 }), cell(2)]);

  // then
  expect(screen.getByRole('button', { name: '게시물, 사진 3장' })).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '게시물' })).toBeOnTheScreen();
  expect(screen.getAllByTestId('multi-media', HIDDEN)).toHaveLength(1);
});

it('shows 숨겨짐 instead of a photo for a hidden post', async () => {
  // given / when
  await show([cell(1, { thumbnailUrl: null, status: 'HIDDEN' })]);

  // then
  expect(screen.getByText('숨겨짐', HIDDEN)).toBeTruthy();
  expect(screen.getByRole('button', { name: '숨겨진 게시물' })).toBeOnTheScreen();
});

it('opens the post when a cell is pressed', async () => {
  // given
  await show([cell(5)]);

  // when
  await fireEvent.press(screen.getByRole('button', { name: '게시물' }));

  // then
  expect(mockPush).toHaveBeenCalledWith('/posts/5');
});
