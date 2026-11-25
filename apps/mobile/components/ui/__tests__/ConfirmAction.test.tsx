import { act, fireEvent, render, screen } from '@testing-library/react-native';

import { ConfirmAction } from '@/components/ui/ConfirmAction';

jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());

const press = (name: string) => fireEvent.press(screen.getByRole('button', { name }));

const show = (onConfirm: () => Promise<boolean>, error: string | null = null) =>
  render(
    <ConfirmAction
      label="내보내기"
      confirmLabel="내보낼게요"
      message="다시 신청할 수 없어요."
      onConfirm={onConfirm}
      error={error}
    />,
  );

it('asks before running and runs once', async () => {
  let finish = (_: boolean) => {};
  const run = jest.fn(() => new Promise<boolean>((resolve) => (finish = resolve)));
  await show(run);
  await press('내보내기');
  expect(run).not.toHaveBeenCalled();
  expect(screen.getByText('다시 신청할 수 없어요.')).toBeOnTheScreen();
  await press('내보낼게요');
  await press('내보낼게요');
  expect(run).toHaveBeenCalledTimes(1);
  await act(async () => finish(true));
  expect(screen.queryByText('다시 신청할 수 없어요.')).toBeNull();
});

it('stays open with the error when it fails', async () => {
  await show(async () => false, '이미 시작한 벙입니다.');
  await press('내보내기');
  await press('내보낼게요');
  expect(screen.getByText('다시 신청할 수 없어요.')).toBeOnTheScreen();
  expect(screen.getByText('이미 시작한 벙입니다.')).toBeOnTheScreen();
});

it('closes without running on 돌아가기', async () => {
  const run = jest.fn(async () => true);
  await show(run);
  await press('내보내기');
  await press('돌아가기');
  expect(run).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: '내보내기' })).toBeOnTheScreen();
});

it('names the target for screen readers', async () => {
  await render(
    <ConfirmAction
      target="별이"
      label="내보내기"
      confirmLabel="내보낼게요"
      message="다시 신청할 수 없어요."
      onConfirm={async () => true}
    />,
  );
  await press('별이 내보내기');
  expect(screen.getByRole('button', { name: '별이 내보낼게요' })).toBeOnTheScreen();
});
