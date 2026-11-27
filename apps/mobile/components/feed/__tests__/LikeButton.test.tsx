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
  // 스프링은 실제 프레임 시간으로 진행하므로 정확히 0이 아닐 수 있다. 끝 상태(1)와 멀기만 하면 된다.
  const { opacity, transform } = fillStyle();
  expect(opacity).toBeLessThan(0.5);
  expect(transform[0].scale).toBeLessThan(0.5);
});

it('animates only the first change after a press, not a later rollback', async () => {
  // given: 누르고 켜진 뒤 연출이 끝난 상태
  const onPress = jest.fn();
  const view = await render(<LikeButton liked={false} count={1} onPress={onPress} />);
  await fireEvent.press(screen.getByRole('button', { name: '좋아요' }));
  await view.rerender(<LikeButton liked count={2} onPress={onPress} />);

  // when: 서버 실패로 되돌아가고 다시 외부에서 켜진다.
  await view.rerender(<LikeButton liked={false} count={1} onPress={onPress} />);
  await view.rerender(<LikeButton liked count={2} onPress={onPress} />);

  // then: 두 번째 이후 변경은 끝 상태로 바로 간다.
  expect(fillStyle()).toMatchObject({ opacity: 1, transform: [{ scale: 1 }] });
});

it('does not wait for an animation when the press changes nothing', async () => {
  // given: 값이 바뀌지 않는 누름(false)
  const view = await render(<LikeButton liked={false} count={1} onPress={() => false} />);
  await fireEvent.press(screen.getByRole('button', { name: '좋아요' }));

  // when: 나중에 외부에서 켜진다.
  await view.rerender(<LikeButton liked count={2} onPress={() => false} />);

  // then
  expect(fillStyle()).toMatchObject({ opacity: 1, transform: [{ scale: 1 }] });
});

it('starts the pop from zero when a double tap arrives one render before liked', async () => {
  // given
  const view = await render(<LikeButton liked={false} count={1} onPress={() => {}} />);

  // when: 두 번 탭으로 celebrate가 먼저 오르고, 캐시 갱신으로 liked는 다음 렌더에 켜진다.
  await view.rerender(<LikeButton liked={false} count={1} celebrate={1} onPress={() => {}} />);
  await view.rerender(<LikeButton liked count={2} celebrate={1} onPress={() => {}} />);

  // then
  const { opacity, transform } = fillStyle();
  expect(opacity).toBeLessThan(0.5);
  expect(transform[0].scale).toBeLessThan(0.5);
});

it('replays the pop when an already liked post is double tapped', async () => {
  // given
  const view = await render(<LikeButton liked count={2} onPress={() => {}} />);

  // when
  await view.rerender(<LikeButton liked count={2} celebrate={1} onPress={() => {}} />);

  // then
  const { opacity, transform } = fillStyle();
  expect(opacity).toBeLessThan(0.5);
  expect(transform[0].scale).toBeLessThan(0.5);
});
