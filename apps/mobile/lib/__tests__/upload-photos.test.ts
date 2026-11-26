import { ImageManipulator } from 'expo-image-manipulator';
import type { ImagePickerAsset } from 'expo-image-picker';

import { preparePhoto, uploadPhotos, type PreparedPhoto } from '@/lib/upload-photos';
import type { PostUploadResponse } from '@/types/api/post';

jest.mock('expo-image-manipulator', () => ({
  SaveFormat: { JPEG: 'jpeg' },
  ImageManipulator: { manipulate: jest.fn() },
}));

const manipulate = ImageManipulator.manipulate as jest.Mock;

/** 저장한 파일의 크기. fetch(file://…)로 읽는다. */
let fileSizes: Record<string, number>;

/** 네이티브처럼 maxWidth·maxHeight 안으로 비율을 지켜 줄이고, 작으면 그대로 둔다. */
function fakeManipulator(source: { width: number; height: number }, savedSizes: number[]) {
  manipulate.mockImplementation(
    (_uri: string, options: { maxWidth: number; maxHeight: number }) => {
      const scale = Math.min(1, options.maxWidth / source.width, options.maxHeight / source.height);
      const width = Math.round(source.width * scale);
      const height = Math.round(source.height * scale);
      let saves = 0;
      const image = {
        width,
        height,
        release: jest.fn(),
        saveAsync: jest.fn(async () => {
          const uri = `file:///cache/saved-${saves}.jpg`;
          fileSizes[uri] = savedSizes[saves];
          saves += 1;
          return { uri, width, height };
        }),
      };
      return { renderAsync: jest.fn(async () => image), release: jest.fn() };
    },
  );
}

function asset(width: number, height: number): ImagePickerAsset {
  return { uri: 'file:///picked/a.heic', width, height };
}

const photo = (n: number): PreparedPhoto => ({
  uri: `file:///cache/${n}.jpg`,
  width: 1080,
  height: 1350,
  byteSize: 3,
  contentType: 'image/jpeg',
});

const upload = (n: number): PostUploadResponse => ({
  mediaId: n,
  url: `https://r2.test/uploads/${n}.jpg?X-Amz-Signature=s`,
  headers: { 'Content-Type': 'image/jpeg', 'Content-Length': '3' },
  expiresAt: '2026-11-26T03:15:00Z',
});

/** file://은 저장한 파일을 돌려주고, R2 PUT은 replies 순서대로 상태를 돌려준다. */
function mockFetch(replies: (number | 'network')[] = []) {
  const puts: { url: string; init: RequestInit }[] = [];
  globalThis.fetch = jest.fn(async (url: string, init?: RequestInit) => {
    if (url.startsWith('file://')) {
      return new Response(new Uint8Array(fileSizes[url] ?? 3));
    }
    puts.push({ url, init: init ?? {} });
    const reply = replies[puts.length - 1] ?? 200;
    if (reply === 'network') {
      throw new TypeError('Network request failed');
    }
    return new Response(null, { status: reply });
  }) as unknown as typeof fetch;
  return puts;
}

beforeEach(() => {
  fileSizes = {};
  manipulate.mockReset();
});

describe('preparePhoto', () => {
  it('resizes only when the long side is over 2048', async () => {
    // given: 긴 변이 넘는 사진, 딱 2048인 사진, 작은 사진
    mockFetch();
    const cases = [
      { source: asset(4032, 3024), expected: [2048, 1536] },
      { source: asset(3024, 4032), expected: [1536, 2048] },
      { source: asset(2048, 1000), expected: [2048, 1000] },
      { source: asset(1600, 1200), expected: [1600, 1200] },
    ];

    for (const { source, expected } of cases) {
      fakeManipulator(source, [2_000_000]);

      // when
      const prepared = await preparePhoto(source);

      // then
      expect(manipulate).toHaveBeenLastCalledWith(source.uri, { maxWidth: 2048, maxHeight: 2048 });
      expect([prepared.width, prepared.height]).toEqual(expected);
    }
  });

  it('saves as JPEG 0.85 and measures the saved file', async () => {
    // given
    mockFetch();
    fakeManipulator(asset(4032, 3024), [2_345_678]);

    // when
    const prepared = await preparePhoto(asset(4032, 3024));

    // then
    const image = await manipulate.mock.results[0].value.renderAsync();
    expect(image.saveAsync).toHaveBeenCalledWith({ compress: 0.85, format: 'jpeg' });
    expect(prepared).toEqual({
      uri: 'file:///cache/saved-0.jpg',
      width: 2048,
      height: 1536,
      byteSize: 2_345_678,
      contentType: 'image/jpeg',
    });
  });

  it('saves again at 0.7 when the first file is over 15MB', async () => {
    // given: 첫 저장이 한도보다 1바이트 크다.
    mockFetch();
    fakeManipulator(asset(2048, 2048), [15_728_641, 15_728_640]);

    // when
    const prepared = await preparePhoto(asset(2048, 2048));

    // then
    const image = await manipulate.mock.results[0].value.renderAsync();
    expect(image.saveAsync).toHaveBeenLastCalledWith({ compress: 0.7, format: 'jpeg' });
    expect(prepared.byteSize).toBe(15_728_640);
    expect(prepared.uri).toBe('file:///cache/saved-1.jpg');
  });

  it('throws when it is still over 15MB at 0.7', async () => {
    // given
    mockFetch();
    fakeManipulator(asset(2048, 2048), [15_728_641, 15_728_641]);

    // when / then
    await expect(preparePhoto(asset(2048, 2048))).rejects.toThrow(
      '사진이 너무 커서 올릴 수 없어요. 다른 사진을 골라 주세요.',
    );
  });

  it('fails without saving again when the saved file is empty', async () => {
    // given
    mockFetch();
    fakeManipulator(asset(2048, 2048), [0, 1000]);

    // when / then: 크기 문제가 아니므로 0.7로 다시 저장하지 않는다.
    await expect(preparePhoto(asset(2048, 2048))).rejects.toThrow(
      '사진을 불러오지 못했어요. 다른 사진을 골라 주세요.',
    );
    const image = await manipulate.mock.results[0].value.renderAsync();
    expect(image.saveAsync).toHaveBeenCalledTimes(1);
  });
});

describe('uploadPhotos', () => {
  it('puts each photo with exactly the signed headers', async () => {
    // given
    const puts = mockFetch();

    // when
    await uploadPhotos([upload(1), upload(2)], [photo(1), photo(2)], () => {});

    // then: 순서대로, API가 준 헤더 그대로, 파일 본문을 보낸다.
    expect(puts.map((put) => put.url)).toEqual([upload(1).url, upload(2).url]);
    expect(puts[0].init.method).toBe('PUT');
    expect(puts[0].init.headers).toEqual(upload(1).headers);
    expect((puts[0].init.body as Blob).size).toBe(3);
  });

  it('retries a failed PUT once and then throws', async () => {
    // given: 첫 사진이 한 번 실패하고 다시 하면 된다. 둘째 사진은 두 번 다 실패한다.
    const puts = mockFetch([500, 200, 'network', 503]);

    // when
    const result = uploadPhotos([upload(1), upload(2)], [photo(1), photo(2)], () => {});

    // then
    await expect(result).rejects.toThrow(
      '사진을 올리지 못했어요. 연결을 확인하고 다시 시도해 주세요.',
    );
    expect(puts.map((put) => put.url)).toEqual([
      upload(1).url,
      upload(1).url,
      upload(2).url,
      upload(2).url,
    ]);
  });

  it('reports progress after each photo', async () => {
    // given
    mockFetch();
    const onProgress = jest.fn();

    // when
    await uploadPhotos(
      [upload(1), upload(2), upload(3)],
      [photo(1), photo(2), photo(3)],
      onProgress,
    );

    // then
    expect(onProgress.mock.calls).toEqual([
      [1, 3],
      [2, 3],
      [3, 3],
    ]);
  });

  it('does not report progress for a photo that failed', async () => {
    // given
    mockFetch([200, 500, 500]);
    const onProgress = jest.fn();

    // when
    await uploadPhotos([upload(1), upload(2)], [photo(1), photo(2)], onProgress).catch(() => {});

    // then
    expect(onProgress.mock.calls).toEqual([[1, 2]]);
  });
});
