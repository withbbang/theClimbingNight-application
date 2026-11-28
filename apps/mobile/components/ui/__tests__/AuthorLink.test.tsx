import { fireEvent, render, screen } from '@testing-library/react-native';

import { AuthorLink } from '@/components/ui/AuthorLink';
import { startAs } from '@/test-utils/session';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }) }));
jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);

beforeEach(() => mockPush.mockClear());

const link = () => screen.queryByRole('link', { name: '달이 프로필 보기' });

it('sends a signed-out user to the login screen', async () => {
  // given
  await startAs(null);
  await render(<AuthorLink userId={7} nickname="달이" imageUrl={null} />);

  // when
  await fireEvent.press(link()!);

  // then
  expect(mockPush).toHaveBeenCalledWith('/login');
});

it('opens the profile for a signed-in user', async () => {
  // given
  await startAs('USER');
  await render(<AuthorLink userId={7} nickname="달이" imageUrl={null} />);

  // when
  await fireEvent.press(link()!);

  // then
  expect(mockPush).toHaveBeenCalledWith('/users/7');
});

it('cannot be pressed for a withdrawn user', async () => {
  // given / when
  await startAs('USER');
  await render(<AuthorLink userId={7} nickname="달이" imageUrl={null} withdrawn />);

  // then
  expect(link()).toBeNull();
});
