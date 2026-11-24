import { addOpenUrlMethod } from '../with-open-url';

const { readFileSync } = jest.requireActual<{
  readFileSync(path: string, encoding: 'utf8'): string;
}>('fs');

const APP_DELEGATE = `@main
class AppDelegate: ExpoAppDelegate {
  var window: UIWindow?
}

class ReactNativeDelegate: ExpoReactNativeFactoryDelegate {
}
`;

it('adds the open url method that the naver plugin rewrites', () => {
  const result = addOpenUrlMethod(APP_DELEGATE);
  expect(result).toContain('open url: URL');
  // 네이버 plugin이 찾는 문자열을 설치된 소스와 맞춰 본다.
  const line =
    'return super.application(app, open: url, options: options) || RCTLinkingManager.application(app, open: url, options: options)';
  const plugin = readFileSync(
    'node_modules/@react-native-seoul/naver-login/plugin/build/index.js',
    'utf8',
  );
  expect(plugin).toContain(`replace('${line}'`);
  expect(result).toContain(line);
  expect(result.indexOf('open url: URL')).toBeLessThan(result.indexOf('class ReactNativeDelegate'));
});

it('leaves an existing method alone', () => {
  const once = addOpenUrlMethod(APP_DELEGATE);
  expect(addOpenUrlMethod(once)).toBe(once);
});

it('fails the build when there is no AppDelegate class', () => {
  expect(() => addOpenUrlMethod('struct App {}')).toThrow('AppDelegate');
});
