process.env.EXPO_PUBLIC_API_URL = 'https://api.test';

require('@shopify/flash-list/jestSetup');
require('react-native-reanimated').setUpTests();
require('react-native-gesture-handler/jestSetup');

// jest의 worklets는 웹 구현이라 UI 런타임이 없다. 제스처 핸들러가 이를 설치하다 죽지 않게 한다.
jest.mock('react-native-gesture-handler/src/handlers/gestures/installUIRuntimeBindings', () => ({
  installUIRuntimeBindings: jest.fn(),
}));
jest.mock('react-native-gesture-handler/lib/module/handlers/gestures/installUIRuntimeBindings', () => ({
  installUIRuntimeBindings: jest.fn(),
}));

// 네이티브 알림이 없는 테스트 기본값. 푸시 테스트는 각자 다시 mock한다.
jest.mock('expo-notifications', () => ({
  setNotificationHandler: jest.fn(),
  useLastNotificationResponse: jest.fn(() => null),
  clearLastNotificationResponse: jest.fn(),
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
  getPermissionsAsync: jest.fn(async () => ({ granted: true })),
}));

// 네이티브 웹뷰가 없는 테스트 기본값.
jest.mock('react-native-webview', () => require('./test-utils/web-view'));
