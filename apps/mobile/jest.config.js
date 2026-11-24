/** @type {import('jest').Config} */
module.exports = {
  watchman: false,
  preset: 'jest-expo',
  resolver: 'react-native-reanimated/jest/resolver.js',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  moduleNameMapper: { '^@/(.*)$': '<rootDir>/$1' },
};
