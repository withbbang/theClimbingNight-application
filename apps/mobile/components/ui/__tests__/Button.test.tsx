import { fireEvent, render, screen } from '@testing-library/react-native';

import { Button } from '@/components/ui/Button';

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(async () => {}),
  ImpactFeedbackStyle: { Light: 'light' },
}));

it('calls onPress', async () => {
  const onPress = jest.fn();
  await render(<Button label="로그인하기" onPress={onPress} />);
  await fireEvent.press(screen.getByRole('button', { name: '로그인하기' }));
  expect(onPress).toHaveBeenCalledTimes(1);
});

it('does not call onPress while disabled', async () => {
  const onPress = jest.fn();
  await render(<Button label="로그인하기" onPress={onPress} disabled />);
  await fireEvent.press(screen.getByRole('button'));
  expect(onPress).not.toHaveBeenCalled();
});

it('ignores a second press while loading', async () => {
  const onPress = jest.fn();
  await render(<Button label="로그인하기" onPress={onPress} />);
  await fireEvent.press(screen.getByRole('button'));
  await screen.rerender(<Button label="로그인하기" onPress={onPress} loading />);
  await fireEvent.press(screen.getByRole('button'));
  expect(onPress).toHaveBeenCalledTimes(1);
});

it('exposes busy and disabled state to accessibility', async () => {
  await render(<Button label="로그인하기" onPress={() => {}} loading />);
  const button = screen.getByRole('button', { name: '로그인하기' });
  expect(button).toBeBusy();
  expect(button).toBeDisabled();
});
