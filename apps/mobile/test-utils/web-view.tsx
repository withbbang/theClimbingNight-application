import { useImperativeHandle, type Ref } from 'react';
import { View } from 'react-native';

import type { WebViewProps } from 'react-native-webview';

/** 마지막으로 그린 웹뷰. 네이티브 이벤트는 props의 콜백을 불러 흉내 낸다. */
export const webView = {
  props: {} as WebViewProps,
  goBack: jest.fn(),
  mounts: 0,
};

interface Handle {
  goBack: () => void;
}

export function WebView({ ref, ...props }: WebViewProps & { ref?: Ref<Handle> }) {
  webView.props = props;
  useImperativeHandle(ref, () => {
    webView.mounts += 1;
    return { goBack: webView.goBack };
  }, []);
  const uri = props.source && 'uri' in props.source ? props.source.uri : '';
  return <View accessible accessibilityLabel={`웹 ${uri}`} />;
}
