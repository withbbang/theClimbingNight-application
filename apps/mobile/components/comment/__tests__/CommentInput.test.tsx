import { QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { CommentInput } from '@/components/comment/CommentInput';
import { queryClient } from '@/lib/query-client';
import { comment } from '@/test-utils/comments';
import type { CommentResponse } from '@/types/api/comment';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
// 키보드 겹침 계산에 넘기는 값을 보려고 KeyboardAvoidingView를 받은 값을 드러내는 View로 바꾼다.
jest.mock('react-native/Libraries/Components/Keyboard/KeyboardAvoidingView', () => {
  const { createElement } = jest.requireActual<typeof import('react')>('react');
  function KeyboardAvoidingView({
    keyboardVerticalOffset,
    children,
  }: {
    keyboardVerticalOffset?: number;
    children?: import('react').ReactNode;
  }) {
    return createElement('View', { testID: 'keyboard-avoiding', keyboardVerticalOffset }, children);
  }
  return { __esModule: true, default: KeyboardAvoidingView };
});
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

async function show(replyTo: CommentResponse | null = null, onCancelReply = jest.fn()) {
  await render(
    <QueryClientProvider client={queryClient}>
      <CommentInput postId={1} replyTo={replyTo} onCancelReply={onCancelReply} />
    </QueryClientProvider>,
  );
  return onCancelReply;
}

const post = () => screen.getByRole('button', { name: '게시' });
const write = (text: string) => fireEvent.changeText(screen.getByLabelText('댓글 입력'), text);

it('disables posting for blank or 501-character text', async () => {
  // given
  await show();
  expect(post()).toBeDisabled();

  // when: 공백만
  await write('    ');
  // then
  expect(post()).toBeDisabled();

  // when: 앞뒤 공백을 자르면 500자
  await write(` ${'가'.repeat(500)} `);
  // then
  expect(post()).toBeEnabled();

  // when: 501자
  await write('가'.repeat(501));
  // then
  expect(post()).toBeDisabled();
});

it('shows the reply header and clears it on cancel', async () => {
  // given
  const onCancel = await show(comment({ id: 4, author: { id: 8, nickname: '바위', profileImageUrl: null } }));
  expect(screen.getByText('바위님에게 답글')).toBeOnTheScreen();

  // when
  await fireEvent.press(screen.getByRole('button', { name: '답글 취소' }));

  // then
  expect(onCancel).toHaveBeenCalledTimes(1);
});

it('has no reply header on a plain comment', async () => {
  await show(null);

  expect(screen.queryByText(/님에게 답글/)).not.toBeOnTheScreen();
});

it('lifts itself by the container offset so the keyboard does not cover it', async () => {
  // given: KeyboardAvoidingView는 부모 기준 위치로 겹침을 재므로, 부모가 창 위에서 내려온 거리를 넘겨야 한다
  await render(
    <QueryClientProvider client={queryClient}>
      <CommentInput postId={1} replyTo={null} onCancelReply={jest.fn()} keyboardOffset={126} />
    </QueryClientProvider>,
  );

  // then
  expect(screen.getByTestId('keyboard-avoiding').props.keyboardVerticalOffset).toBe(126);
});
