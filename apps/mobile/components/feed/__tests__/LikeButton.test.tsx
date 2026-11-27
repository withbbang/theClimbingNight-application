import { fireEvent, render, screen } from '@testing-library/react-native';

import { LikeButton } from '@/components/feed/LikeButton';

jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

/** 채운 하트의 지금 스타일. 연출이 시작되면 0에서 출발한다. */
const fillStyle = () =>
  screen.getByTestId('like-fill', { includeHiddenElements: true }).props.jestAnimatedStyle.value;

it('snaps to the end state without animating when liked changes without a press', async () => {
  // given: 재활용된 셀에 다른 글이 들어와 liked만 바뀐다.
  const view = await render(<LikeButton liked={false} count={1} onPress={() => {}} />);

  // when
  await view.rerender(<LikeButton liked count={2} onPress={() => {}} />);

  // then: 0에서 시작하는 튕김 없이 바로 끝 상태다.
  expect(fillStyle()).toMatchObject({ opacity: 1, transform: [{ scale: 1 }] });
});

it('starts the pop from zero after a press', async () => {
  // given
  const onPress = jest.fn();
  const view = await render(<LikeButton liked={false} count={1} onPress={onPress} />);

  // when: 누르고 낙관적으로 켜진다.
  await fireEvent.press(screen.getByRole('button', { name: '좋아요' }));
  await view.rerender(<LikeButton liked count={2} onPress={onPress} />);

  // then
  expect(onPress).toHaveBeenCalled();
  expect(fillStyle()).toMatchObject({ opacity: 0, transform: [{ scale: 0 }] });
});
