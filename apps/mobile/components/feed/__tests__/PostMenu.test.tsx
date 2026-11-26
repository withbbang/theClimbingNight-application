import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react-native';

import { PostMenu } from '@/components/feed/PostMenu';
import { queryClient } from '@/lib/query-client';
import { post } from '@/test-utils/posts';
import { startAs } from '@/test-utils/session';
import type { Role } from '@/types/api/user';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), back: jest.fn() }) }));
jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

async function show(role: Role, mine: boolean) {
  await startAs(role);
  await render(
    <QueryClientProvider client={queryClient}>
      <PostMenu post={post({ mine })} onClose={() => {}} />
    </QueryClientProvider>,
  );
}

const button = (name: string) => screen.queryByRole('button', { name });

it('shows edit and delete only on my post', async () => {
  // given: 내 글
  await show('USER', true);

  // then
  expect(button('수정')).toBeOnTheScreen();
  expect(button('삭제')).toBeOnTheScreen();
  expect(button('신고')).toBeNull();
  expect(button('달이님 차단')).toBeNull();

  // when: 남의 글
  screen.unmount();
  await show('USER', false);

  // then
  expect(button('수정')).toBeNull();
  expect(button('삭제')).toBeNull();
  expect(button('신고')).toBeOnTheScreen();
  expect(button('달이님 차단')).toBeOnTheScreen();
});

it('shows 숨기기 only to admins', async () => {
  // given: 남의 글을 일반 회원이 본다
  await show('MEMBER', false);
  expect(button('숨기기')).toBeNull();

  // when: 운영진이 본다
  screen.unmount();
  await show('ADMIN', false);

  // then
  expect(button('숨기기')).toBeOnTheScreen();
});
