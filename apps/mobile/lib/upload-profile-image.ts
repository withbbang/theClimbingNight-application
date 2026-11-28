/**
 * 프로필 사진을 1024px JPEG로 줄여 presigned URL로 R2에 올린다. 게시물 사진과 같은 규칙(`upload-photos.ts`)을 쓴다.
 * presigned URL 전체는 비밀값이라 오류 메시지·로그에 넣지 않는다.
 */
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import type { ImagePickerAsset } from 'expo-image-picker';

import { PhotoError, put, readFile, type PreparedPhoto } from '@/lib/upload-photos';
import type { ProfileImageUploadResponse } from '@/types/api/profile';

export const PROFILE_LONG_SIDE = 1024;
/** API가 받는 프로필 사진의 최대 크기(5MB). */
export const MAX_PROFILE_PHOTO_BYTES = 5_242_880;
const QUALITY = 0.85;
const FALLBACK_QUALITY = 0.7;

/** 고른 사진(이미 1:1로 잘림)을 긴 변 1024px JPEG 0.85로 저장한다. 5MB를 넘으면 0.7로 한 번 더 저장한다. */
export async function prepareProfilePhoto(asset: ImagePickerAsset): Promise<PreparedPhoto> {
  const context = ImageManipulator.manipulate(asset.uri, {
    maxWidth: PROFILE_LONG_SIDE,
    maxHeight: PROFILE_LONG_SIDE,
  });
  try {
    const image = await context.renderAsync();
    try {
      for (const compress of [QUALITY, FALLBACK_QUALITY]) {
        const saved = await image.saveAsync({ compress, format: SaveFormat.JPEG });
        const byteSize = (await readFile(saved.uri)).size;
        if (byteSize === 0) {
          throw new PhotoError('사진을 불러오지 못했어요. 다른 사진을 골라 주세요.');
        }
        if (byteSize <= MAX_PROFILE_PHOTO_BYTES) {
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

/** 실패하면 한 번 더 해 보고, 또 실패하면 멈춘다. */
export async function uploadProfilePhoto(
  upload: ProfileImageUploadResponse,
  photo: Pick<PreparedPhoto, 'uri'>,
): Promise<void> {
  try {
    const body = await readFile(photo.uri);
    await put(upload, body).catch(() => put(upload, body));
  } catch {
    throw new PhotoError('사진을 올리지 못했어요. 연결을 확인하고 다시 시도해 주세요.');
  }
}
