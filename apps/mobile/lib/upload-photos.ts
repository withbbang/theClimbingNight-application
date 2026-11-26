/**
 * 고른 사진을 줄여 JPEG로 저장하고, API가 발급한 presigned URL로 R2에 직접 올린다.
 *
 * 규칙 예외: R2 PUT은 우리 API가 아니어서 `apiFetch`를 쓰지 않는다. 앱에서 `fetch`를 직접 부르는 곳은 여기뿐이다.
 * (저장한 파일을 읽는 `fetch(file://…)`도 네트워크 요청이 아니다.) presigned URL 전체는 비밀값이라 오류 메시지·로그에 넣지 않는다.
 */
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import type { ImagePickerAsset } from 'expo-image-picker';

import type { PostUploadResponse } from '@/types/api/post';

/** 긴 변의 최대 길이. 더 작으면 키우지 않는다. */
export const MAX_LONG_SIDE = 2048;
/** API가 받는 사진 한 장의 최대 크기(15MB). */
export const MAX_PHOTO_BYTES = 15_728_640;
const QUALITY = 0.85;
const FALLBACK_QUALITY = 0.7;

export interface PreparedPhoto {
  uri: string;
  width: number;
  height: number;
  /** PUT으로 보내는 파일의 실제 크기. presigned URL이 Content-Length로 서명한다. */
  byteSize: number;
  contentType: 'image/jpeg';
}

/** 사용자에게 그대로 보여 줄 수 있는 사진 오류. */
export class PhotoError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PhotoError';
  }
}

async function readFile(uri: string): Promise<Blob> {
  const response = await fetch(uri);
  return response.blob();
}

/** 긴 변 2048px 이하, JPEG 0.85로 저장한다. 15MB를 넘으면 0.7로 한 번 더 저장한다. */
export async function preparePhoto(asset: ImagePickerAsset): Promise<PreparedPhoto> {
  // 디코딩할 때부터 줄여서 큰 원본을 메모리에 다 올리지 않는다. 방향(EXIF)도 네이티브가 맞춘다.
  const context = ImageManipulator.manipulate(asset.uri, {
    maxWidth: MAX_LONG_SIDE,
    maxHeight: MAX_LONG_SIDE,
  });
  try {
    const image = await context.renderAsync();
    try {
      for (const compress of [QUALITY, FALLBACK_QUALITY]) {
        const saved = await image.saveAsync({ compress, format: SaveFormat.JPEG });
        const byteSize = (await readFile(saved.uri)).size;
        if (byteSize > 0 && byteSize <= MAX_PHOTO_BYTES) {
          return {
            uri: saved.uri,
            width: saved.width,
            height: saved.height,
            byteSize,
            contentType: 'image/jpeg',
          };
        }
      }
    } finally {
      image.release();
    }
  } finally {
    context.release();
  }
  throw new PhotoError('사진이 너무 커서 올릴 수 없어요. 다른 사진을 골라 주세요.');
}

async function put(upload: PostUploadResponse, body: Blob) {
  const response = await fetch(upload.url, { method: 'PUT', headers: upload.headers, body });
  if (!response.ok) {
    throw new Error(`R2 PUT ${response.status}`);
  }
}

/** 한 장씩 순서대로 올린다. 실패하면 한 번 더 해 보고, 또 실패하면 멈춘다. */
export async function uploadPhotos(
  uploads: PostUploadResponse[],
  photos: PreparedPhoto[],
  onProgress: (done: number, total: number) => void,
): Promise<void> {
  const failed = new PhotoError('사진을 올리지 못했어요. 연결을 확인하고 다시 시도해 주세요.');
  if (uploads.length !== photos.length) {
    throw failed;
  }
  for (let i = 0; i < photos.length; i += 1) {
    try {
      const body = await readFile(photos[i].uri);
      await put(uploads[i], body).catch(() => put(uploads[i], body));
    } catch {
      throw failed;
    }
    onProgress(i + 1, photos.length);
  }
}
