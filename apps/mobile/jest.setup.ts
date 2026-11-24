process.env.EXPO_PUBLIC_API_URL = 'https://api.test';

require('@shopify/flash-list/jestSetup');
require('react-native-reanimated').setUpTests();

// 네이티브 알림이 없는 테스트 기본값. 푸시 테스트는 각자 다시 mock한다.
jest.mock('expo-notifications', () => ({
  setNotificationHandler: jest.fn(),
  useLastNotificationResponse: jest.fn(() => null),
  clearLastNotificationResponse: jest.fn(),
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
}));
