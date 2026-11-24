import { fireEvent, render, screen } from '@testing-library/react-native';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { sessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }) }));
jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

beforeEach(async () => {
  mockPush.mockClear();
  await sessionStore.signOut();
});

const show = (role: 'USER' | 'MEMBER') =>
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
] as const)('shows the children to %s when %s is needed', async (mine, needed) => {
  await sessionStore.signIn(tokens('1', mine));
  await show(needed);
  expect(screen.getByText('안쪽')).toBeOnTheScreen();
});
