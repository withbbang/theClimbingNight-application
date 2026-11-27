import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react-native';

import { CommentMenu } from '@/components/comment/CommentMenu';
import { queryClient } from '@/lib/query-client';
import { comment } from '@/test-utils/comments';
import { startAs } from '@/test-utils/session';
import type { CommentResponse } from '@/types/api/comment';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

async function show(item: CommentResponse) {
  await startAs('USER');
  await render(
    <QueryClientProvider client={queryClient}>
      <CommentMenu postId={1} comment={item} onClose={() => {}} />
    </QueryClientProvider>,
  );
}

const button = (name: string) => screen.queryByRole('button', { name });

it('offers delete only when deletable', async () => {
  // given: 지울 수 있는 내 댓글
  await show(comment({ mine: true, deletable: true }));
  expect(button('삭제')).toBeOnTheScreen();

  // when: 지울 수 없는 댓글
  screen.unmount();
  await show(comment({ mine: false, deletable: false }));

  // then
  expect(button('삭제')).toBeNull();
});

it("offers report and block on others' comments", async () => {
  // given: 남의 댓글
  await show(comment({ mine: false }));

  // then
  expect(button('신고')).toBeOnTheScreen();
  expect(button('바위님 차단')).toBeOnTheScreen();

  // when: 내 댓글
  screen.unmount();
  await show(comment({ mine: true, deletable: true }));

  // then
  expect(button('신고')).toBeNull();
  expect(button('바위님 차단')).toBeNull();
});
