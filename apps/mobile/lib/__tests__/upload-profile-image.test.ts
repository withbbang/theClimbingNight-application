import { ImageManipulator } from 'expo-image-manipulator';
import type { ImagePickerAsset } from 'expo-image-picker';

import { prepareProfilePhoto, uploadProfilePhoto } from '@/lib/upload-profile-image';
import type { ProfileImageUploadResponse } from '@/types/api/profile';

jest.mock('expo-image-manipulator', () => ({
  SaveFormat: { JPEG: 'jpeg' },
  ImageManipulator: { manipulate: jest.fn() },
}));

const manipulate = ImageManipulator.manipulate as jest.Mock;
let fileSizes: Record<string, number>;

function fakeManipulator(savedSizes: number[], size = 1024) {
  manipulate.mockImplementation(() => {
    let saves = 0;
    const image = {
      width: size,
      height: size,
      release: jest.fn(),
      saveAsync: jest.fn(async () => {
        const uri = `file:///cache/p-${saves}.jpg`;
        fileSizes[uri] = savedSizes[saves];
        saves += 1;
        return { uri, width: size, height: size };
      }),
    };
    return { renderAsync: jest.fn(async () => image), release: jest.fn() };
  });
}

const asset: ImagePickerAsset = { uri: 'file:///picked/a.jpg', width: 3000, height: 3000 };
const upload: ProfileImageUploadResponse = {
  id: 3,
  url: 'https://r2.test/uploads/x.jpg?X-Amz-Signature=s',
  headers: { 'Content-Type': 'image/jpeg', 'Content-Length': '3' },
  expiresAt: '2026-11-26T03:15:00Z',
};

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

describe('prepareProfilePhoto', () => {
  it('shrinks to 1024px and saves JPEG 0.85', async () => {
    // given
    mockFetch();
    fakeManipulator([300_000]);

    // when
    const prepared = await prepareProfilePhoto(asset);

    // then
    expect(manipulate).toHaveBeenCalledWith(asset.uri, { maxWidth: 1024, maxHeight: 1024 });
    const image = await manipulate.mock.results[0].value.renderAsync();
    expect(image.saveAsync).toHaveBeenCalledWith({ compress: 0.85, format: 'jpeg' });
    expect(prepared).toEqual({
      uri: 'file:///cache/p-0.jpg',
      width: 1024,
      height: 1024,
      byteSize: 300_000,
      contentType: 'image/jpeg',
    });
  });

  it('saves again at 0.7 when over 5MB', async () => {
    // given
    mockFetch();
    fakeManipulator([5_242_881, 4_000_000]);

    // when
    const prepared = await prepareProfilePhoto(asset);

    // then
    const image = await manipulate.mock.results[0].value.renderAsync();
    expect(image.saveAsync).toHaveBeenLastCalledWith({ compress: 0.7, format: 'jpeg' });
    expect(prepared.byteSize).toBe(4_000_000);
  });

  it('keeps a file of exactly 5MB', async () => {
    mockFetch();
    fakeManipulator([5_242_880]);

    const prepared = await prepareProfilePhoto(asset);

    expect(prepared.byteSize).toBe(5_242_880);
  });

  it('throws PhotoError when still over 5MB at 0.7', async () => {
    mockFetch();
    fakeManipulator([5_242_881, 5_242_881]);

    await expect(prepareProfilePhoto(asset)).rejects.toThrow(
      '사진이 너무 커서 올릴 수 없어요. 다른 사진을 골라 주세요.',
    );
  });
});

describe('uploadProfilePhoto', () => {
  it('puts the file with exactly the signed headers', async () => {
    // given
    const puts = mockFetch();

    // when
    await uploadProfilePhoto(upload, { uri: 'file:///cache/p.jpg' });

    // then
    expect(puts).toHaveLength(1);
    expect(puts[0].url).toBe(upload.url);
    expect(puts[0].init.method).toBe('PUT');
    expect(puts[0].init.headers).toEqual(upload.headers);
  });

  it('retries once and then throws a message without the url', async () => {
    const puts = mockFetch([500, 503]);

    const result = uploadProfilePhoto(upload, { uri: 'file:///cache/p.jpg' });

    await expect(result).rejects.toThrow(
      '사진을 올리지 못했어요. 연결을 확인하고 다시 시도해 주세요.',
    );
    expect(puts).toHaveLength(2);
  });
});
