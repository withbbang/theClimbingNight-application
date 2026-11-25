import { fireEvent, render, screen } from '@testing-library/react-native';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { sessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

const mockPush = jest.fn();
const mockDismissTo = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, dismissTo: mockDismissTo }),
}));
jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

beforeEach(async () => {
  mockPush.mockClear();
  await sessionStore.signOut();
});

const show = (role: 'USER' | 'MEMBER' | 'ADMIN') =>
  render(
    <RoleGate role={role}>
      <AppText>안쪽</AppText>
    </RoleGate>,
  );

it('shows the login notice to a guest', async () => {
  await show('USER');
  expect(screen.queryByText('안쪽')).toBeNull();
  await fireEvent.press(screen.getByRole('button', { name: '로그인하기' }));
  expect(mockPush).toHaveBeenCalledWith('/login');
});

it('sends a USER to verification when MEMBER is needed', async () => {
  await sessionStore.signIn(tokens('1', 'USER'));
  await show('MEMBER');
  expect(screen.queryByText('안쪽')).toBeNull();
  await fireEvent.press(screen.getByRole('button', { name: '인증하기' }));
  expect(mockPush).toHaveBeenCalledWith('/membership');
});

it.each([
  ['USER', 'USER'],
  ['MEMBER', 'MEMBER'],
  ['ADMIN', 'MEMBER'],
  ['ADMIN', 'ADMIN'],
] as const)('shows the children to %s when %s is needed', async (mine, needed) => {
  await sessionStore.signIn(tokens('1', mine));
  await show(needed);
  expect(screen.getByText('안쪽')).toBeOnTheScreen();
});

it.each(['USER', 'MEMBER'] as const)('blocks %s from admin children', async (mine) => {
  await sessionStore.signIn(tokens('1', mine));
  await show('ADMIN');
  expect(screen.queryByText('안쪽')).toBeNull();
  expect(screen.getByText('운영진만 볼 수 있어요')).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole('button', { name: '홈으로' }));
  expect(mockDismissTo).toHaveBeenCalledWith('/');
});
