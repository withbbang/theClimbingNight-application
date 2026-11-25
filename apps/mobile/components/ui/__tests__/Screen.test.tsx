import { Text } from 'react-native';

import { render, screen } from '@testing-library/react-native';

import { Screen } from '@/components/ui/Screen';

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

it('lets the scroll view bring the focused field above the keyboard on iOS', async () => {
  await render(
    <Screen scroll>
      <Text>내용</Text>
    </Screen>,
  );
  expect(JSON.stringify(screen.toJSON())).toContain('"automaticallyAdjustKeyboardInsets":true');
});
