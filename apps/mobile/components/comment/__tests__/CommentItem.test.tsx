import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react-native';

import { CommentItem } from '@/components/comment/CommentItem';
import { queryClient } from '@/lib/query-client';
import { comment } from '@/test-utils/comments';
import type { CommentResponse } from '@/types/api/comment';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);

async function show(item: CommentResponse) {
  await render(
    <QueryClientProvider client={queryClient}>
      <CommentItem postId={1} comment={item} />
    </QueryClientProvider>,
  );
}

it('shows the deleted placeholder without author', async () => {
  await show(comment({ display: 'DELETED', author: null, body: null }));

  expect(screen.getByText('삭제된 댓글입니다')).toBeOnTheScreen();
  expect(screen.queryByText('바위')).not.toBeOnTheScreen();
});

it('shows the policy notice on my hidden comment', async () => {
  await show(comment({ display: 'HIDDEN_MINE', mine: true }));

  expect(screen.getByText('운영 정책에 따라 숨겨졌어요')).toBeOnTheScreen();
});

it('shows the hidden placeholder to others', async () => {
  await show(comment({ display: 'HIDDEN', author: null, body: null }));

  expect(screen.getByText('숨겨진 댓글입니다')).toBeOnTheScreen();
});

it('prefixes replies with @unknown when the target left', async () => {
  await show(comment({ id: 2, parentId: 1, replyToNickname: null }));

  expect(screen.getByText('@알 수 없음')).toBeOnTheScreen();
});

it('prefixes replies with the target nickname', async () => {
  await show(comment({ id: 2, parentId: 1, replyToNickname: '달이' }));

  expect(screen.getByText('@달이')).toBeOnTheScreen();
});
