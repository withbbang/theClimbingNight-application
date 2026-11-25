import {
  Appearance,
  AppState,
  DeviceEventEmitter,
  Platform,
  TurboModuleRegistry,
} from 'react-native';

import { renderHook } from '@testing-library/react-native';

import { useSyncColorScheme } from '@/hooks/use-sync-color-scheme';

let device: 'light' | 'dark';
let change: (state: string) => void;
let emit: jest.SpyInstance;

const announced = () => emit.mock.calls.filter(([name]) => name === 'appearanceChanged');

beforeEach(() => {
  device = 'light';
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(TurboModuleRegistry, 'get').mockReturnValue({ getColorScheme: () => device } as never);
  // React Native가 읽어 둔 값
  jest.spyOn(Appearance, 'getColorScheme').mockReturnValue('light');
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_type, listener) => {
    change = listener as typeof change;
    return { remove: jest.fn() } as never;
  });
  emit = jest.spyOn(DeviceEventEmitter, 'emit');
});

afterEach(() => jest.restoreAllMocks());

// 액티비티가 다시 만들어지면 React Native가 색상 변경을 알리지 않는다.
it('announces a color scheme that changed while the app was away', async () => {
  await renderHook(() => useSyncColorScheme());

  device = 'dark';
  change('active');

  expect(announced()).toEqual([['appearanceChanged', { colorScheme: 'dark' }]]);
});

it('stays quiet when nothing changed or the app is leaving', async () => {
  await renderHook(() => useSyncColorScheme());

  change('active');
  device = 'dark';
  change('background');

  expect(announced()).toEqual([]);
});
