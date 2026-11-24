import * as SecureStore from 'expo-secure-store';

import { createSessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

const mockStored = new Map<string, string>();

jest.mock('expo-secure-store', () => ({
  AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY: 1,
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

beforeEach(() => {
  mockStored.clear();
  jest
    .mocked(SecureStore.getItemAsync)
    .mockReset()
    .mockImplementation(async (key) => mockStored.get(key) ?? null);
  jest
    .mocked(SecureStore.setItemAsync)
    .mockReset()
    .mockImplementation(async (key, value) => {
      mockStored.set(key, value);
    });
  jest
    .mocked(SecureStore.deleteItemAsync)
    .mockReset()
    .mockImplementation(async (key) => {
      mockStored.delete(key);
    });
});

it('becomes guest when nothing is stored', async () => {
  const store = createSessionStore();
  expect(store.getState()).toEqual({ status: 'loading' });
  await store.load();
  expect(store.getState()).toEqual({ status: 'guest' });
});

it('restores a stored session', async () => {
  mockStored.set('session', JSON.stringify(tokens('1')));
  const store = createSessionStore();
  await store.load();
  expect(store.getState()).toEqual({ status: 'authenticated', tokens: tokens('1') });
});

it.each([
  ['it is not valid JSON', '{'],
  ['it is null', 'null'],
  ['a required field is missing', JSON.stringify({ ...tokens('1'), refreshToken: undefined })],
  ['the user is missing', JSON.stringify({ ...tokens('1'), user: undefined })],
])('becomes guest and deletes the value when %s', async (_name, value) => {
  mockStored.set('session', value);
  const store = createSessionStore();
  await store.load();
  expect(store.getState()).toEqual({ status: 'guest' });
  expect(mockStored.has('session')).toBe(false);
});

it('becomes guest when secure store throws', async () => {
  jest.mocked(SecureStore.getItemAsync).mockRejectedValue(new Error('keychain'));
  const store = createSessionStore();
  await store.load();
  expect(store.getState()).toEqual({ status: 'guest' });
});

it('does not overwrite a sign-in that happened while loading', async () => {
  let finish: (value: string | null) => void = () => {};
  jest.mocked(SecureStore.getItemAsync).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const store = createSessionStore();
  const loading = store.load();
  await store.signIn(tokens('2'));
  finish(null);
  await loading;
  expect(store.getState()).toEqual({ status: 'authenticated', tokens: tokens('2') });
});

it('discards updateTokens from an old generation', async () => {
  const store = createSessionStore();
  await store.signIn(tokens('1'));
  const generation = store.getGeneration();
  await store.signOut();
  await expect(store.updateTokens(tokens('2'), generation)).resolves.toBe(false);
  expect(store.getState()).toEqual({ status: 'guest' });
  expect(mockStored.has('session')).toBe(false);
});

it('keeps the generation when tokens or the user are updated', async () => {
  const store = createSessionStore();
  await store.signIn(tokens('1'));
  const generation = store.getGeneration();
  await expect(store.updateTokens(tokens('2'), generation)).resolves.toBe(true);
  await store.updateUser({ id: 1, nickname: '새별', role: 'MEMBER' });
  expect(store.getGeneration()).toBe(generation);
  const expected = { ...tokens('2'), user: { id: 1, nickname: '새별', role: 'MEMBER' } };
  expect(store.getState()).toEqual({ status: 'authenticated', tokens: expected });
  expect(JSON.parse(mockStored.get('session')!)).toEqual(expected);
});

it('ignores a user update that belongs to another account', async () => {
  const store = createSessionStore();
  await store.signIn(tokens('1'));
  await store.updateUser({ id: 99, nickname: '남', role: 'ADMIN' });
  expect(store.getState()).toEqual({ status: 'authenticated', tokens: tokens('1') });
});

it('persists writes in call order', async () => {
  let finish: () => void = () => {};
  jest.mocked(SecureStore.setItemAsync).mockImplementation(
    (key, value) =>
      new Promise((resolve) => {
        finish = () => {
          mockStored.set(key, value);
          resolve();
        };
      }),
  );
  const store = createSessionStore();
  const signIn = store.signIn(tokens('1'));
  const signOut = store.signOut();
  await Promise.resolve();
  await Promise.resolve();
  finish();
  await Promise.all([signIn, signOut]);
  expect(mockStored.has('session')).toBe(false);
  expect(store.getState()).toEqual({ status: 'guest' });
});

it('keeps the in-memory session when a write fails', async () => {
  jest.mocked(SecureStore.setItemAsync).mockRejectedValue(new Error('keychain'));
  const store = createSessionStore();
  await store.signIn(tokens('1'));
  expect(store.getState()).toEqual({ status: 'authenticated', tokens: tokens('1') });
});

it('notifies subscribers on change until they unsubscribe', async () => {
  const store = createSessionStore();
  const listener = jest.fn();
  const unsubscribe = store.subscribe(listener);
  await store.signIn(tokens('1'));
  expect(listener).toHaveBeenCalledTimes(1);
  unsubscribe();
  await store.signOut();
  expect(listener).toHaveBeenCalledTimes(1);
});
