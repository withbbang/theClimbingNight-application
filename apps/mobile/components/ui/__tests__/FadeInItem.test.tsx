import { Text } from 'react-native';

import { act, render, screen } from '@testing-library/react-native';
import { getAnimatedStyle, useReducedMotion } from 'react-native-reanimated';

import { FadeInItem } from '@/components/ui/FadeInItem';

jest.mock('react-native-reanimated', () => ({
  ...jest.requireActual('react-native-reanimated'),
  __esModule: true,
  useReducedMotion: jest.fn(),
}));

beforeEach(() => jest.useFakeTimers());
afterEach(() => jest.useRealTimers());

// 배포된 타입 선언에 인자와 반환값이 빠져 있다.
const readStyle = getAnimatedStyle as unknown as (node: unknown) => {
  opacity: number;
  transform: { translateY: number }[];
};
const style = () => readStyle(screen.getByTestId('item'));

it('starts hidden and ends visible', async () => {
  jest.mocked(useReducedMotion).mockReturnValue(false);
  await render(
    <FadeInItem index={0} testID="item">
      <Text>벙</Text>
    </FadeInItem>,
  );
  expect(style().opacity).toBe(0);
  await act(async () => {
    jest.advanceTimersByTime(3000);
  });
  expect(style().opacity).toBeCloseTo(1, 1);
});

it('renders the final state when reduce motion is on', async () => {
  jest.mocked(useReducedMotion).mockReturnValue(true);
  await render(
    <FadeInItem index={5} testID="item">
      <Text>벙</Text>
    </FadeInItem>,
  );
  expect(style()).toMatchObject({ opacity: 1, transform: [{ translateY: 0 }] });
});
