import { nicknameError } from '@/lib/nickname';

it.each(['가나', 'abc_1', '가'.repeat(16), '  가나  '])('accepts "%s"', (value) => {
  expect(nicknameError(value)).toBeNull();
});

it.each(['', '가', '가'.repeat(17), 'a b', 'a-b', 'ㄱㄴ'])('rejects "%s"', (value) => {
  expect(nicknameError(value)).toBe(
    '닉네임은 2~16자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다.',
  );
});
