/** 사진이 없으면 ProfileResponse.profileImage가 null이다. */
export interface ProfileImageUrls {
  /** 320px */
  largeUrl: string;
  /** 96px */
  smallUrl: string;
}

/** bio는 없으면 null. postCount는 보이는 글 수. */
export interface ProfileResponse {
  id: number;
  nickname: string;
  bio: string | null;
  profileImage: ProfileImageUrls | null;
  postCount: number;
  mine: boolean;
}

/** thumbnailUrl은 숨긴 글이면 null, status는 본인 요청에만 있다. */
export interface GridPostResponse {
  id: number;
  thumbnailUrl: string | null;
  mediaCount: number;
  status: string | null;
}

/** url 전체는 비밀값이다. 로그에 남기지 않는다. */
export interface ProfileImageUploadResponse {
  id: number;
  url: string;
  headers: Record<string, string>;
  expiresAt: string;
}

export type ProfileImageStatus = 'UPLOADING' | 'PROCESSING' | 'READY' | 'FAILED' | 'REMOVED';

export interface ProfileImageStatusResponse {
  id: number;
  status: ProfileImageStatus;
}
