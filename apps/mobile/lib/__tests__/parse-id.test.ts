import { parseId } from '@/lib/parse-id';

it.each([
  ['7', 7],
  [7, 7],
  ['0', null],
  ['-1', null],
  ['1.5', null],
  [1.5, null],
  ['abc', null],
  ['', null],
  [undefined, null],
  [['7'], null],
  ['9007199254740992', null],
])('parses %p to %p', (value, expected) => {
  expect(parseId(value)).toBe(expected);
});
