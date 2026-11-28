import { createElement, type ReactNode } from 'react';

import { act, renderHook } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { useChangeNickname } from '@/hooks/use-profile';
import { sessionStore } from '@/lib/session-store';
import { userApi } from '@/lib/user-api';
import { tokens } from '@/test-utils/tokens';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('@/lib/sign-out', () => ({ signOut: jest.fn() }));
jest.mock('@/lib/social/apple', () => ({ appleAuthorizationCode: jest.fn() }));
jest.mock('@/lib/user-api', () => ({
  userApi: { changeNickname: jest.fn(), withdraw: jest.fn() },
}));

let client: QueryClient;
afterEach(() => client.clear());
beforeEach(() => jest.resetAllMocks());

it('invalidates my profile query after changing the nickname', async () => {
  await sessionStore.signOut();
  await sessionStore.signIn(tokens('1', 'MEMBER'));
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  jest.mocked(userApi.changeNickname).mockResolvedValue({ id: 1, nickname: '새이름', role: 'MEMBER' });
  client.setQueryData(['users', 1, 'profile'], { id: 1, nickname: '옛이름' });

  const { result } = await renderHook(() => useChangeNickname(), { wrapper });
  await act(async () => {
    await result.current.mutateAsync('새이름');
  });

  expect(client.getQueryState(['users', 1, 'profile'])?.isInvalidated).toBe(true);
  expect(client.getQueryData(['users', 'me'])).toEqual({ id: 1, nickname: '새이름', role: 'MEMBER' });
});
