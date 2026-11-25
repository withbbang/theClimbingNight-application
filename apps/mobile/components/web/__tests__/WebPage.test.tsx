import { BackHandler, Linking, StyleSheet, Text } from 'react-native';

import { router, Stack, type Href } from 'expo-router';
import { act, fireEvent, renderRouter, screen } from 'expo-router/testing-library';

import { WebPage } from '@/components/web/WebPage';
import { webView } from '@/test-utils/web-view';

const URL = 'https://api.test/app/notices';
const LABEL = `웹 ${URL}`;

let openURL: jest.SpyInstance;
type HardwareBack = Parameters<typeof BackHandler.addEventListener>[1];
let hardware: HardwareBack[];
/** 라우터가 먼저 등록한 처리. */
let routerHandlers: HardwareBack[];
let addListener: jest.SpyInstance;

beforeEach(() => {
  webView.goBack.mockClear();
  webView.mounts = 0;
  hardware = [];
  routerHandlers = [];
  openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined);
  addListener = jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_name, handler) => {
    hardware = [...hardware, handler];
    return { remove: () => (hardware = hardware.filter((h) => h !== handler)) };
  });
});
afterEach(() => {
  openURL.mockRestore();
  addListener.mockRestore();
});

function Layout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}

async function open(tab = false) {
  const app = await renderRouter(
    {
      _layout: Layout,
      index: () => <Text>처음</Text>,
      web: () => <WebPage path="/app/notices" tab={tab} />,
      other: () => <Text>다른 화면</Text>,
    },
    { initialUrl: '/' },
  );
  await act(async () => router.push('/web' as Href));
  routerHandlers = hardware;
  return app;
}

const ours = () => hardware.filter((handler) => !routerHandlers.includes(handler));

type Request = Parameters<NonNullable<typeof webView.props.onShouldStartLoadWithRequest>>[0];

async function request(url: string, extra: object = {}) {
  let allowed: boolean | undefined;
  await act(async () => {
    allowed = webView.props.onShouldStartLoadWithRequest?.({ url, ...extra } as Request);
  });
  return allowed;
}

async function emit(name: keyof typeof webView.props, nativeEvent: object = {}) {
  await act(async () => {
    (webView.props[name] as (event: object) => void)({ nativeEvent });
  });
}

async function navigated(canGoBack: boolean) {
  await act(async () => {
    webView.props.onNavigationStateChange?.({ canGoBack } as never);
  });
}

async function pressBack() {
  await act(async () => fireEvent.press(screen.getByRole('button', { name: '뒤로' })));
}

it('opens our page', async () => {
  await open();
  expect(screen.getByLabelText(LABEL)).toBeTruthy();
  expect(await request('https://api.test/app/notices/1', { isTopFrame: true })).toBe(true);
  expect(openURL).not.toHaveBeenCalled();
});

it.each([{ isTopFrame: true }, {}, { isTopFrame: false, hasTargetFrame: false }])(
  'opens outside links in the browser and stays (%p)',
  async (extra) => {
    await open();
    expect(await request('https://store.test/x', extra)).toBe(false);
    expect(openURL.mock.calls).toEqual([['https://store.test/x']]);
  },
);

it('does not open the browser for subframes', async () => {
  await open();
  const frame = { isTopFrame: false, hasTargetFrame: true };
  expect(await request('https://ads.test/', frame)).toBe(false);
  expect(openURL).not.toHaveBeenCalled();
});

it('blocks app links', async () => {
  await open();
  expect(await request('intent://x#Intent;end')).toBe(false);
  expect(openURL).not.toHaveBeenCalled();
});

it('tells when the browser does not open', async () => {
  openURL.mockRejectedValue(new Error('no browser'));
  await open();
  await request('https://store.test/x');
  expect(screen.getByText('링크를 열지 못했어요.')).toBeTruthy();
});

it('goes back inside the page before leaving', async () => {
  const app = await open();
  await navigated(true);
  await pressBack();
  expect(webView.goBack).toHaveBeenCalledTimes(1);
  expect(app.getPathname()).toBe('/web');

  await navigated(false);
  await pressBack();
  expect(app.getPathname()).toBe('/');
});

it('takes the hardware back only while the page has history', async () => {
  await open();
  await navigated(true);
  expect(ours().map((handler) => handler({} as never))).toEqual([true]);
  expect(webView.goBack).toHaveBeenCalledTimes(1);

  await navigated(false);
  expect(ours()).toHaveLength(0);
});

it('does not take the hardware back on an unfocused screen', async () => {
  await open();
  await navigated(true);
  expect(ours()).toHaveLength(1);
  await act(async () => router.push('/other' as Href));
  expect(ours()).toHaveLength(0);
});

it('has no back button in a tab until the page has history', async () => {
  await open(true);
  expect(screen.queryByRole('button', { name: '뒤로' })).toBeNull();

  await navigated(true);
  await pressBack();
  expect(webView.goBack).toHaveBeenCalledTimes(1);
});

it('never hides the web view behind its own opacity', async () => {
  // iOS는 투명한 채로 받은 페이지를 나중에 그리지 않는다.
  await open();
  let node = screen.getByLabelText(LABEL).parent;
  while (node) {
    const { opacity } = StyleSheet.flatten(node.props.style ?? {}) as { opacity?: number };
    expect(opacity ?? 1).toBe(1);
    node = node.parent;
  }
});

it('shows loading until the page arrives', async () => {
  await open();
  expect(screen.getByLabelText('불러오는 중')).toBeTruthy();
  await emit('onLoadEnd');
  expect(screen.queryByLabelText('불러오는 중')).toBeNull();
});

it.each([
  ['onError', {}],
  ['onHttpError', { statusCode: 502 }],
] as const)('replaces a page failed by %s with a retry', async (name, event) => {
  await open();
  await emit(name, event);
  expect(screen.queryByLabelText(LABEL)).toBeNull();
  expect(screen.getByText('불러오지 못했어요')).toBeTruthy();

  await act(async () => fireEvent.press(screen.getByRole('button', { name: '다시 시도' })));
  expect(screen.getByLabelText(LABEL)).toBeTruthy();
  expect(screen.getByLabelText('불러오는 중')).toBeTruthy();
});

it('leaves a failed page with the back button', async () => {
  const app = await open();
  await navigated(true);
  await emit('onError');
  await pressBack();
  expect(app.getPathname()).toBe('/');
});

it('keeps the page for a 404', async () => {
  await open();
  await emit('onHttpError', { statusCode: 404 });
  expect(screen.getByLabelText(LABEL)).toBeTruthy();
});

it.each(['onContentProcessDidTerminate', 'onRenderProcessGone'] as const)(
  'recreates the page after %s',
  async (name) => {
    await open();
    await navigated(true);
    expect(webView.mounts).toBe(1);
    await emit(name);
    expect(webView.mounts).toBe(2);
    expect(ours()).toHaveLength(0);
  },
);

// Android는 JS가 제때 답하지 않으면 주소를 그대로 연다.
it.each(['onLoadStart', 'onError'] as const)(
  'starts over when another site slips in: %s',
  async (name) => {
    await open();
    await emit('onLoadEnd');

    await emit(name, { url: 'https://evil.test/' });

    expect(webView.mounts).toBe(2);
  },
);

it('asks to retry instead of looping when the first page goes elsewhere', async () => {
  await open();

  await emit('onLoadStart', { url: 'https://login.test/' });

  expect(await screen.findByText('불러오지 못했어요')).toBeOnTheScreen();
  expect(webView.mounts).toBe(1);
});

it('keeps our own pages while they load', async () => {
  await open();

  await emit('onLoadStart', { url: URL });
  await emit('onLoadStart', { url: 'about:blank' });

  expect(webView.mounts).toBe(1);
});
