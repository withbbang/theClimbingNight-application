import { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, Linking, StyleSheet, View } from 'react-native';

import { useFocusEffect } from 'expo-router';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { WebView, type WebViewNavigation } from 'react-native-webview';

import { BackButton } from '@/components/ui/BackButton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Notice } from '@/components/ui/Notice';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { useTheme } from '@/hooks/use-theme';
import { decideLink, webUrl, type WebPath } from '@/lib/web-url';

interface LoadRequest {
  url: string;
  /** Android는 최상위 프레임의 이동만 알리고 이 값을 주지 않는다. */
  isTopFrame?: boolean;
  /** iOS에서 새 창으로 여는 링크면 false. */
  hasTargetFrame?: boolean;
}

interface ViewerProps {
  path: WebPath;
  tab: boolean;
  onFail: () => void;
  onGone: () => void;
}

function Viewer({ path, tab, onFail, onGone }: ViewerProps) {
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const view = useRef<WebView<object>>(null);
  const [loaded, setLoaded] = useState(false);
  const [canGoBack, setCanGoBack] = useState(false);
  const [linkFailed, setLinkFailed] = useState(false);
  const shown = useSharedValue(0);

  useEffect(() => {
    if (loaded) {
      shown.set(reduced ? 1 : withSpring(1, motion.spring));
    }
  }, [loaded, motion.spring, reduced, shown]);

  const animated = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, shown.get())),
    transform: [{ translateY: reduced ? 0 : 16 * (1 - shown.get()) }],
  }));

  useFocusEffect(
    useCallback(() => {
      if (!canGoBack) {
        return;
      }
      const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
        view.current?.goBack();
        return true;
      });
      return () => subscription.remove();
    }, [canGoBack]),
  );

  function shouldStart({ url, isTopFrame, hasTargetFrame }: LoadRequest) {
    const decision = decideLink(url);
    const subframe = isTopFrame === false && hasTargetFrame !== false;
    if (decision === 'outside' && !subframe) {
      setLinkFailed(false);
      Linking.openURL(url).catch(() => setLinkFailed(true));
    }
    return decision === 'inside';
  }

  return (
    <View style={styles.fill}>
      {!tab || canGoBack ? (
        <View style={{ paddingHorizontal: space(6) }}>
          <BackButton onPress={canGoBack ? () => view.current?.goBack() : undefined} />
        </View>
      ) : null}
      {linkFailed ? (
        <View style={{ paddingHorizontal: space(6), paddingBottom: space(3) }}>
          <ErrorBanner message="링크를 열지 못했어요." />
        </View>
      ) : null}
      <View style={[styles.fill, { paddingBottom: tab ? 0 : insets.bottom }]}>
        <Animated.View style={[styles.fill, animated]}>
          <WebView<object>
            ref={view}
            source={{ uri: webUrl(path) }}
            style={{ backgroundColor: colors.background }}
            // 라이브러리가 다른 앱을 열지 않게 하고 직접 판정한다.
            originWhitelist={['*']}
            onShouldStartLoadWithRequest={shouldStart}
            onNavigationStateChange={(state: WebViewNavigation) => setCanGoBack(state.canGoBack)}
            onLoadEnd={() => setLoaded(true)}
            onError={onFail}
            onHttpError={({ nativeEvent }) => {
              if (nativeEvent.statusCode >= 500) {
                onFail();
              }
            }}
            onContentProcessDidTerminate={onGone}
            onRenderProcessGone={onGone}
            setSupportMultipleWindows={false}
            javaScriptCanOpenWindowsAutomatically={false}
            allowFileAccess={false}
            sharedCookiesEnabled={false}
          />
        </Animated.View>
        {loaded ? null : (
          <View
            accessible
            accessibilityLabel="불러오는 중"
            pointerEvents="none"
            style={[StyleSheet.absoluteFill, { padding: space(6) }]}
          >
            <PulseHolds color={colors.accent} />
          </View>
        )}
      </View>
    </View>
  );
}

interface WebPageProps {
  path: WebPath;
  /** 탭 화면은 돌아갈 곳이 없고 탭 막대가 아래 여백을 맡는다. */
  tab?: boolean;
}

/** apps/web 페이지를 여는 화면. 우리 페이지가 아닌 링크는 시스템 브라우저로 넘긴다. */
export function WebPage({ path, tab = false }: WebPageProps) {
  const { colors, space } = useTheme();
  const insets = useSafeAreaInsets();
  const [attempt, setAttempt] = useState(0);
  const [failed, setFailed] = useState(false);

  return (
    <View
      style={[
        styles.fill,
        { backgroundColor: colors.background, paddingTop: insets.top + space(4) },
      ]}
    >
      {failed ? (
        <View style={{ paddingHorizontal: space(6) }}>
          {tab ? null : <BackButton />}
          <Notice
            title="불러오지 못했어요"
            description="네트워크 연결을 확인한 뒤 다시 시도해 주세요."
            actionLabel="다시 시도"
            onAction={() => setFailed(false)}
          />
        </View>
      ) : (
        <Viewer
          key={`${path} ${attempt}`}
          path={path}
          tab={tab}
          onFail={() => setFailed(true)}
          onGone={() => setAttempt((n) => n + 1)}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
