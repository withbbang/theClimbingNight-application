import { screen } from 'expo-router/testing-library';

import { mockServer, ok, renderApp } from '@/test-utils/app';
import { post } from '@/test-utils/posts';
import { press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const PATCH = 'PATCH /posts/5';

beforeEach(() => startAs('USER'));

it('fills the body once and saves only the body', async () => {
  // given
  const server = mockServer({
    '/posts/5': ok(post({ id: 5, body: '예전 본문', mine: true, status: 'VISIBLE' })),
    [PATCH]: ok(post({ id: 5, body: '고친 본문', mine: true, status: 'VISIBLE' })),
  });
  await renderApp('/posts/5/edit');
  expect(screen.getByDisplayValue('예전 본문')).toBeOnTheScreen();

  // when
  await type('본문', ' 고친 본문 ');
  await press('저장');

  // then
  expect(server.calls.find((call) => `${call.method} ${call.path}` === PATCH)?.body).toEqual({
    body: '고친 본문',
  });
});

it('does not open the form for a post that is not mine', async () => {
  // given
  const server = mockServer({ '/posts/5': ok(post({ id: 5, mine: false })) });

  // when
  await renderApp('/posts/5/edit');

  // then
  expect(screen.getByText('고칠 수 없는 글이에요')).toBeOnTheScreen();
  expect(screen.queryByRole('button', { name: '저장' })).toBeNull();
  expect(server.count(PATCH)).toBe(0);
});
