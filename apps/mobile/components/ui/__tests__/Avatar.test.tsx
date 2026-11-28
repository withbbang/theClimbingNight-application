import { act, render, screen } from '@testing-library/react-native';

import { Avatar } from '@/components/ui/Avatar';

// 아바타는 스크린 리더에서 가려져 있다.
const HIDDEN = { includeHiddenElements: true };

jest.mock('expo-image', () => ({
  Image: (props: object) =>
    jest.requireActual('react').createElement('Image', { ...props, testID: 'avatar-image' }),
}));

it('renders the image from the given url', async () => {
  // given / when
  await render(<Avatar nickname="달이" imageUrl="https://media.test/a/1/s.webp" />);

  // then
  expect(screen.getByTestId('avatar-image', HIDDEN).props.source).toEqual({
    uri: 'https://media.test/a/1/s.webp',
  });
  expect(screen.queryByText('달', HIDDEN)).toBeNull();
});

it('shows the first letter when there is no image url', async () => {
  await render(<Avatar nickname="달이" />);

  expect(screen.getByText('달', HIDDEN)).toBeTruthy();
  expect(screen.queryByTestId('avatar-image', HIDDEN)).toBeNull();
});

it('falls back to the first letter when the image fails to load', async () => {
  // given
  await render(<Avatar nickname="달이" imageUrl="https://media.test/a/1/s.webp" />);

  // when
  await act(async () => {
    screen.getByTestId('avatar-image', HIDDEN).props.onError({ error: 'x' });
  });

  // then
  expect(screen.getByText('달', HIDDEN)).toBeTruthy();
  expect(screen.queryByTestId('avatar-image', HIDDEN)).toBeNull();
});
