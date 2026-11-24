import { fireEvent, render, screen } from '@testing-library/react-native';

import { AnimatedTabBar, type AnimatedTabBarProps } from '@/components/navigation/AnimatedTabBar';

jest.mock('expo-haptics', () => ({ selectionAsync: jest.fn(async () => {}) }));

function props(index: number): AnimatedTabBarProps {
  const names = ['index', 'meetups', 'goods', 'my'];
  return {
    state: { index, routes: names.map((name) => ({ key: name, name })) },
    emitter: { emit: jest.fn(() => ({ defaultPrevented: false })) },
    navigateToTab: jest.fn(),
    insets: { top: 0, right: 0, bottom: 34, left: 0 },
  };
}

it('marks only the focused tab as selected', async () => {
  await render(<AnimatedTabBar {...props(1)} />);
  expect(screen.getByRole('tab', { name: '벙' })).toBeSelected();
  expect(screen.getByRole('tab', { name: '홈' })).not.toBeSelected();
});

it('navigates to another tab', async () => {
  const p = props(0);
  await render(<AnimatedTabBar {...p} />);
  await fireEvent.press(screen.getByRole('tab', { name: '마이' }));
  expect(p.emitter.emit).toHaveBeenCalledWith({
    type: 'tabPress',
    target: 'my',
    canPreventDefault: true,
  });
  expect(p.navigateToTab).toHaveBeenCalledWith('my');
});

it('does not navigate when the focused tab is pressed or the event is prevented', async () => {
  const p = props(0);
  await render(<AnimatedTabBar {...p} />);
  await fireEvent.press(screen.getByRole('tab', { name: '홈' }));
  jest.mocked(p.emitter.emit).mockReturnValue({ defaultPrevented: true });
  await fireEvent.press(screen.getByRole('tab', { name: '벙' }));
  expect(p.navigateToTab).not.toHaveBeenCalled();
});
