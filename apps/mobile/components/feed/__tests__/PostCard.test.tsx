import { fireEvent, render, screen } from '@testing-library/react-native';

import { PostCard } from '@/components/feed/PostCard';
import { media, post } from '@/test-utils/posts';

jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

const LONG = '첫 줄\n둘째 줄\n셋째 줄\n넷째 줄';

it('shows the image count dots only when there are two or more', async () => {
  // given
  const view = await render(<PostCard post={post({ media: [media(11)] })} />);
  expect(screen.queryByLabelText(/장 중 \d+번째 사진/)).toBeNull();

  // when
  await view.rerender(<PostCard post={post({ media: [media(11), media(12)] })} />);

  // then
  expect(screen.getByLabelText('2장 중 1번째 사진')).toBeOnTheScreen();
});

it('collapses the body after three lines and expands on 더 보기', async () => {
  // given
  await render(<PostCard post={post({ body: LONG })} />);
  expect(screen.getByText(LONG)).toHaveProp('numberOfLines', 3);
  expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();

  // when: 전체 본문이 네 줄로 그려진다.
  const measure = screen.getByTestId('post-body-measure', { includeHiddenElements: true });
  await fireEvent(measure, 'textLayout', {
    nativeEvent: { lines: [{}, {}, {}, {}] },
  });
  await fireEvent.press(screen.getByRole('button', { name: '더 보기' }));

  // then
  expect(screen.getByText(LONG).props.numberOfLines).toBeUndefined();
  expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();
});

it('collapses again when the body changes', async () => {
  // given: 펼쳐 둔 본문
  const view = await render(<PostCard post={post({ body: LONG })} />);
  const measure = screen.getByTestId('post-body-measure', { includeHiddenElements: true });
  await fireEvent(measure, 'textLayout', { nativeEvent: { lines: [{}, {}, {}, {}] } });
  await fireEvent.press(screen.getByRole('button', { name: '더 보기' }));

  // when: 고친 글을 다시 받는다.
  const edited = `${LONG}\n다섯째 줄`;
  await view.rerender(<PostCard post={post({ body: edited })} />);

  // then
  expect(screen.getByText(edited)).toHaveProp('numberOfLines', 3);
});
