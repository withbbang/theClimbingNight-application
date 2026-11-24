/** @type {import('jest').Config} */
module.exports = {
  watchman: false,
  preset: 'jest-expo',
  resolver: 'react-native-reanimated/jest/resolver.js',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  transformIgnorePatterns: [
    '/node_modules/(?!(.pnpm|react-native|@react-native|@react-native-community|expo|@expo|phosphor-react-native|@shopify/flash-list|standard-navigation))',
    '/node_modules/react-native-reanimated/plugin/',
    '/node_modules/@react-native/babel-preset/',
  ],
  moduleNameMapper: { '^@/(.*)$': '<rootDir>/$1' },
};
