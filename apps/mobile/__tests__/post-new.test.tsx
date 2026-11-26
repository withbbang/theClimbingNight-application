import { launchImageLibraryAsync, type ImagePickerAsset } from 'expo-image-picker';
import { screen } from 'expo-router/testing-library';

import { preparePhoto, uploadPhotos } from '@/lib/upload-photos';
import { mockServer, ok, renderApp } from '@/test-utils/app';
import { post } from '@/test-utils/posts';
import { button, press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());
jest.mock('expo-image-picker', () => ({ launchImageLibraryAsync: jest.fn() }));
jest.mock('@/lib/upload-photos', () => ({
  ...jest.requireActual('@/lib/upload-photos'),
  preparePhoto: jest.fn(),
  uploadPhotos: jest.fn(),
}));

const LIMIT_MESSAGE = '본문은 2,200자까지 쓸 수 있어요.';
const CREATE = 'POST /posts';
const PUBLISH = 'POST /posts/31/publish';
const draft = {
  postId: 31,
  uploads: [
    {
      mediaId: 1,
      url: 'https://r2.test/1',
      headers: { 'Content-Type': 'image/jpeg', 'Content-Length': '1001' },
      expiresAt: '2026-11-24T03:15:00Z',
    },
  ],
};

function pickReturns(count: number) {
  const assets: ImagePickerAsset[] = Array.from({ length: count }, (_, i) => ({
    uri: `file:///picked/${i + 1}.heic`,
    width: 4032,
    height: 3024,
  }));
  jest.mocked(launchImageLibraryAsync).mockResolvedValue({ canceled: false, assets });
}

beforeEach(async () => {
  await startAs('USER');
  jest.mocked(launchImageLibraryAsync).mockReset();
  jest.mocked(uploadPhotos).mockReset().mockResolvedValue();
  jest
    .mocked(preparePhoto)
    .mockReset()
    .mockImplementation(async (asset) => ({
      uri: asset.uri.replace('/picked/', '/cache/').replace('.heic', '.jpg'),
      width: 2048,
      height: 1536,
      byteSize: 1001,
      contentType: 'image/jpeg',
    }));
});

const upload = () => button('올리기');

it('opens the new post screen at /posts/new instead of a post detail', async () => {
  // given
  const server = mockServer({});

  // when
  await renderApp('/posts/new');

  // then: [id] 상세가 "new"를 ID로 받지 않는다.
  expect(screen.getByRole('header', { name: '사진 올리기' })).toBeOnTheScreen();
  expect(screen.queryByText('게시물을 찾을 수 없어요')).toBeNull();
  expect(server.calls.filter((call) => call.path.startsWith('/posts'))).toEqual([]);
});

it('disables 올리기 until a photo is picked', async () => {
  // given
  mockServer({});
  pickReturns(2);
  await renderApp('/posts/new');
  expect(upload()).toBeDisabled();

  // when
  await press('사진 고르기');

  // then: 10장까지 순서대로 고르게 한다.
  expect(launchImageLibraryAsync).toHaveBeenCalledWith(
    expect.objectContaining({
      allowsMultipleSelection: true,
      selectionLimit: 10,
      orderedSelection: true,
    }),
  );
  expect(screen.getByLabelText('1번째 사진')).toBeOnTheScreen();
  expect(screen.getByLabelText('2번째 사진')).toBeOnTheScreen();
  expect(upload()).toBeEnabled();
});

it('shows the count limit message over 2200 characters', async () => {
  // given
  mockServer({});
  pickReturns(1);
  await renderApp('/posts/new');
  await press('사진 고르기');

  // when: 한도 그대로
  await type('본문', '가'.repeat(2200));

  // then
  expect(screen.queryByText(LIMIT_MESSAGE)).toBeNull();
  expect(screen.getByText('2200 / 2200')).toBeOnTheScreen();
  expect(upload()).toBeEnabled();

  // when: 한 글자 넘으면
  await type('본문', '가'.repeat(2201));

  // then
  expect(screen.getByText(LIMIT_MESSAGE)).toBeOnTheScreen();
  expect(upload()).toBeDisabled();
});

it('publishes the picked photos and goes back to the feed', async () => {
  // given
  const server = mockServer({
    [CREATE]: ok(draft),
    [PUBLISH]: ok(post({ id: 31, status: 'PENDING', mine: true })),
    '/posts?size=10': ok({ items: [], nextCursor: null }),
    '/posts/mine/in-progress': ok([post({ id: 31, status: 'PENDING', mine: true })]),
  });
  pickReturns(1);
  await renderApp('/posts/new');
  await press('사진 고르기');
  await type('본문', '  오늘 초록 완등  ');

  // when
  await press('올리기');

  // then
  expect(server.calls.find((call) => `${call.method} ${call.path}` === CREATE)?.body).toEqual({
    media: [{ contentType: 'image/jpeg', byteSize: 1001, width: 2048, height: 1536 }],
  });
  expect(server.calls.find((call) => `${call.method} ${call.path}` === PUBLISH)?.body).toEqual({
    body: '오늘 초록 완등',
  });
  expect(screen.getByText('사진 정리 중…')).toBeOnTheScreen();
});

it('shows the error and lets me try again when an upload fails', async () => {
  // given
  const server = mockServer({ [CREATE]: ok(draft), [PUBLISH]: ok(post({ id: 31 })) });
  jest
    .mocked(uploadPhotos)
    .mockRejectedValueOnce(
      new (jest.requireActual('@/lib/upload-photos').PhotoError)(
        '사진을 올리지 못했어요. 연결을 확인하고 다시 시도해 주세요.',
      ),
    );
  pickReturns(1);
  await renderApp('/posts/new');
  await press('사진 고르기');

  // when
  await press('올리기');

  // then
  expect(
    screen.getByText('사진을 올리지 못했어요. 연결을 확인하고 다시 시도해 주세요.'),
  ).toBeOnTheScreen();
  expect(server.count(PUBLISH)).toBe(0);

  // when
  await press('다시 시도');

  // then
  expect(server.count(PUBLISH)).toBe(1);
});
