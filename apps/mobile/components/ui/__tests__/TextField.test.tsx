import { fireEvent, render, screen } from '@testing-library/react-native';

import { TextField } from '@/components/ui/TextField';

it('labels the input and reports changes', async () => {
  const onChangeText = jest.fn();
  await render(<TextField label="닉네임" value="" onChangeText={onChangeText} />);
  await fireEvent.changeText(screen.getByLabelText('닉네임'), '별이');
  expect(onChangeText).toHaveBeenCalledWith('별이');
});

it('announces the error instead of the helper text', async () => {
  await render(
    <TextField
      label="닉네임"
      value=""
      onChangeText={() => {}}
      helper="2~16자"
      error="이미 사용 중인 닉네임입니다."
    />,
  );
  expect(screen.getByRole('alert')).toHaveTextContent('이미 사용 중인 닉네임입니다.');
  expect(screen.queryByText('2~16자')).toBeNull();
});

it('shows the helper text when there is no error', async () => {
  await render(<TextField label="닉네임" value="" onChangeText={() => {}} helper="2~16자" />);
  expect(screen.getByText('2~16자')).toBeOnTheScreen();
  expect(screen.queryByRole('alert')).toBeNull();
});
