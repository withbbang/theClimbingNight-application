import { QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { ReportSheet } from '@/components/feed/ReportSheet';
import { queryClient } from '@/lib/query-client';
import { fail, mockServer } from '@/test-utils/app';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

const REPORT = 'POST /reports';

async function show() {
  await startAs('USER');
  await render(
    <QueryClientProvider client={queryClient}>
      <ReportSheet targetType="POST" targetId={5} onClose={() => {}} />
    </QueryClientProvider>,
  );
}

async function flush() {
  await act(async () => {
    await jest.advanceTimersByTimeAsync(20);
  });
}

const send = () => screen.getByRole('button', { name: '신고 보내기' });

it('requires a detail when 기타 is chosen', async () => {
  // given
  const server = mockServer({ [REPORT]: { status: 204 } });
  await show();

  // when: 기타를 고르고 비워 둔다
  await fireEvent.press(screen.getByRole('radio', { name: '기타' }));
  expect(screen.getByLabelText('자세한 내용').props.maxLength).toBe(500);
  await fireEvent.changeText(screen.getByLabelText('자세한 내용'), '   ');

  // then
  expect(send()).toBeDisabled();

  // when: 내용을 적는다
  await fireEvent.changeText(screen.getByLabelText('자세한 내용'), ' 같은 글을 계속 올려요 ');
  await fireEvent.press(send());
  await flush();

  // then
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({
    targetType: 'POST',
    targetId: 5,
    reason: 'OTHER',
    detail: '같은 글을 계속 올려요',
  });
  expect(screen.getByText('신고를 접수했어요. 운영진이 확인할게요.')).toBeOnTheScreen();
});

it('treats an already submitted report as success', async () => {
  // given
  mockServer({ [REPORT]: fail(409, 'REPORT_ALREADY_SUBMITTED', '이미 신고한 항목입니다.') });
  await show();

  // when
  await fireEvent.press(screen.getByRole('radio', { name: '스팸·광고' }));
  await fireEvent.press(send());
  await flush();

  // then
  expect(screen.getByText('신고를 접수했어요. 운영진이 확인할게요.')).toBeOnTheScreen();
  expect(screen.queryByText('이미 신고한 항목입니다.')).toBeNull();
});

it('treats a post that is already gone as success', async () => {
  // given: 자동 숨김 등으로 이미 내려간 글
  mockServer({ [REPORT]: fail(404, 'COMMON_NOT_FOUND', '찾을 수 없습니다.') });
  await show();

  // when
  await fireEvent.press(screen.getByRole('radio', { name: '음란·선정' }));
  await fireEvent.press(send());
  await flush();

  // then
  expect(screen.getByText('신고를 접수했어요. 운영진이 확인할게요.')).toBeOnTheScreen();
});
