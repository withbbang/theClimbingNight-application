import { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, Linking, StyleSheet, useWindowDimensions, View } from 'react-native';

import { useFocusEffect } from 'expo-router';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
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
  /** 웹뷰 프로세스가 죽었다. */
  onCrash: () => void;
}

/** 이 안에 다시 죽으면 되풀이로 보고 안내를 보인다. */
const CRASH_WINDOW_MS = 30_000;

function Viewer({ path, tab, onFail, onGone, onCrash }: ViewerProps) {
  const { colors, space } = useTheme();
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { fontScale } = useWindowDimensions();
  const view = useRef<WebView<object>>(null);
  const [loaded, setLoaded] = useState(false);
  const [canGoBack, setCanGoBack] = useState(false);
  const [linkFailed, setLinkFailed] = useState(false);
  const cover = useSharedValue(1);

  useEffect(() => {
    if (loaded) {
      cover.set(reduced ? 0 : withTiming(0, { duration: 260 }));
    }
  }, [cover, loaded, reduced]);

  // 웹뷰를 투명하게 두면 iOS가 페이지를 그리지 않는다. 위를 덮었다가 걷는다.
  const covering = useAnimatedStyle(() => ({ opacity: cover.get() }));

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
    if (!loaded && !subframe && strayed(url)) {
      // 첫 페이지의 이동을 취소하면 완료도 오류도 오지 않는다.
      onFail();
      return false;
    }
    if (decision === 'outside' && !subframe) {
      setLinkFailed(false);
      Linking.openURL(url).catch(() => setLinkFailed(true));
    }
    return decision === 'inside';
  }

  /** Android는 JS가 바쁘면 판정을 기다리지 않고 연다. 이미 열리기 시작했으면 처음 페이지로 되돌린다. */
  function strayed(url: string | undefined) {
    return Boolean(url) && !url?.startsWith('about:') && decideLink(url ?? '') !== 'inside';
  }

  function startOver() {
    // 첫 페이지부터 벗어나면 다시 만들어도 같으니 안내를 보인다.
    if (loaded) {
      onGone();
    } else {
      onFail();
    }
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
        <WebView<object>
          ref={view}
          source={{ uri: webUrl(path) }}
          style={{ backgroundColor: colors.background }}
          // Android 전용. 열어 둔 채 글꼴 크기를 바꿔도 따라가게 한다.
          textZoom={Math.round(fontScale * 100)}
          // 라이브러리가 다른 앱을 열지 않게 하고 직접 판정한다.
          originWhitelist={['*']}
          onShouldStartLoadWithRequest={shouldStart}
          onNavigationStateChange={(state: WebViewNavigation) => setCanGoBack(state.canGoBack)}
          onLoadEnd={({ nativeEvent }) => {
            // Android는 실패해도 완료를 먼저 알린다. 벗어난 주소는 연 것으로 치지 않는다.
            if (!strayed(nativeEvent.url)) {
              setLoaded(true);
            }
          }}
          onLoadStart={({ nativeEvent }) => {
            if (strayed(nativeEvent.url)) {
              startOver();
            }
          }}
          onError={({ nativeEvent }) => (strayed(nativeEvent.url) ? startOver() : onFail())}
          onHttpError={({ nativeEvent }) => {
            if (nativeEvent.statusCode >= 500) {
              onFail();
            }
          }}
          onContentProcessDidTerminate={onCrash}
          onRenderProcessGone={onCrash}
          setSupportMultipleWindows={false}
          javaScriptCanOpenWindowsAutomatically={false}
          allowFileAccess={false}
          sharedCookiesEnabled={false}
        />
        <Animated.View
          pointerEvents="none"
          accessible={!loaded}
          accessibilityLabel={loaded ? undefined : '불러오는 중'}
          style={[
            StyleSheet.absoluteFill,
            { padding: space(6), backgroundColor: colors.background },
            covering,
          ]}
        >
          {loaded ? null : <PulseHolds color={colors.accent} />}
        </Animated.View>
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
  const crashedAt = useRef<number | null>(null);

  function crashed() {
    const now = Date.now();
    const again = crashedAt.current !== null && now - crashedAt.current < CRASH_WINDOW_MS;
    crashedAt.current = now;
    if (again) {
      setFailed(true);
    } else {
      setAttempt((n) => n + 1);
    }
  }

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
          onCrash={crashed}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
