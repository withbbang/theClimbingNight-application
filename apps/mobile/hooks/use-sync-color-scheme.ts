import { useEffect } from 'react';
import {
  Appearance,
  AppState,
  DeviceEventEmitter,
  Platform,
  TurboModuleRegistry,
  type TurboModule,
} from 'react-native';

interface NativeAppearance extends TurboModule {
  getColorScheme: () => 'light' | 'dark';
}

/**
 * 앱을 벗어난 사이 바뀐 다크 모드를 반영한다. Android는 글꼴 크기·언어가 바뀌어 액티비티가 다시 만들어지면
 * 색상 변경을 알리지 않고, React Native는 읽어 둔 옛 값을 계속 쓴다.
 */
export function useSyncColorScheme() {
  useEffect(() => {
    if (Platform.OS !== 'android') {
      return;
    }
    const subscription = AppState.addEventListener('change', (state) => {
      if (state !== 'active') {
        return;
      }
      const colorScheme = TurboModuleRegistry.get<NativeAppearance>('Appearance')?.getColorScheme();
      if (colorScheme && colorScheme !== Appearance.getColorScheme()) {
        // React Native의 Appearance가 듣는 이벤트. 값과 화면을 함께 고친다.
        DeviceEventEmitter.emit('appearanceChanged', { colorScheme });
      }
    });
    return () => subscription.remove();
  }, []);
}
