import { withAppDelegate, type ConfigPlugin } from 'expo/config-plugins';

const METHOD = `
  public override func application(
    _ app: UIApplication,
    open url: URL,
    options: [UIApplication.OpenURLOptionsKey: Any] = [:]
  ) -> Bool {
    return super.application(app, open: url, options: options) || RCTLinkingManager.application(app, open: url, options: options)
  }
`;

const CLASS_HEADER = /class AppDelegate\b[^{]*\{\n/;

export function addOpenUrlMethod(contents: string): string {
  if (contents.includes('open url: URL')) {
    return contents;
  }
  if (!CLASS_HEADER.test(contents)) {
    throw new Error('AppDelegate 클래스를 찾지 못해 로그인 URL 처리를 넣을 수 없습니다.');
  }
  return contents.replace(CLASS_HEADER, (header) => header + METHOD);
}

/** 네이버 plugin이 고쳐 쓸 URL 처리 메서드를 넣는다. SDK 58 템플릿에는 이 메서드가 없다. */
const withOpenUrl: ConfigPlugin = (config) =>
  withAppDelegate(config, (props) => {
    props.modResults.contents = addOpenUrlMethod(props.modResults.contents);
    return props;
  });

export default withOpenUrl;
