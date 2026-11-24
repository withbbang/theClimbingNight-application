const expoConfig = require('eslint-config-expo/flat');
const { defineConfig } = require('eslint/config');

module.exports = defineConfig([
  expoConfig,
  { ignores: ['ios/*', 'android/*', 'dist/*', '.expo/*'] },
  { rules: { '@typescript-eslint/no-explicit-any': 'error' }, files: ['**/*.ts', '**/*.tsx'] },
  { files: ['jest.setup.ts'], rules: { '@typescript-eslint/no-require-imports': 'off' } },
]);
