import { act, render, screen } from '@testing-library/react-native';
import { getAnimatedStyle, useReducedMotion } from 'react-native-reanimated';

import { GridCell, MAX_STAGGER } from '@/components/profile/GridCell';
import type { GridPostResponse } from '@/types/api/profile';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('react-native-reanimated', () => ({
  ...jest.requireActual('react-native-reanimated'),
  __esModule: true,
  useReducedMotion: jest.fn(),
}));

beforeEach(() => {
  jest.useFakeTimers();
  jest.mocked(useReducedMotion).mockReturnValue(false);
});
afterEach(() => jest.useRealTimers());

// 배포된 타입 선언에 인자와 반환값이 빠져 있다.
const readStyle = getAnimatedStyle as unknown as (node: unknown) => { opacity: number };
const opacity = () => readStyle(screen.getByTestId('grid-cell')).opacity;

const post = (id: number): GridPostResponse => ({
  id,
  thumbnailUrl: `https://media.test/p/${id}/t.webp`,
  mediaCount: 1,
  status: null,
});

it('lets a cell on the first screen fade in and remembers it', async () => {
  // given
  const shown = new Set<number>();

  // when
  await render(<GridCell post={post(5)} index={0} shown={shown} />);

  // then
  expect(opacity()).toBe(0);
  await act(async () => {
    jest.advanceTimersByTime(3000);
  });
  expect(opacity()).toBeCloseTo(1, 1);
  expect(shown.has(5)).toBe(true);
});

it('shows a cell that already appeared at once when it is remounted', async () => {
  // given: 스크롤로 재활용돼 다시 그려지는 칸
  const shown = new Set([5]);

  // when
  await render(<GridCell post={post(5)} index={0} shown={shown} />);

  // then
  expect(opacity()).toBe(1);
});

it('shows a cell met while scrolling at once', async () => {
  // given / when
  await render(<GridCell post={post(40)} index={MAX_STAGGER} shown={new Set()} />);

  // then
  expect(opacity()).toBe(1);
});
