/** status는 작성자 본인에게만 값이 있고 남에게는 null이다. */
export type PostStatus = 'DRAFT' | 'PENDING' | 'VISIBLE' | 'FAILED' | 'HIDDEN_PENDING' | 'HIDDEN';

export interface PostAuthorResponse {
  id: number;
  nickname: string;
}

/** 아직 정리 중이거나 글이 숨겨졌으면 url·thumbnailUrl은 null이다. */
export interface PostMediaResponse {
  id: number;
  type: 'IMAGE';
  url: string | null;
  thumbnailUrl: string | null;
  width: number;
  height: number;
}

export interface PostResponse {
  id: number;
  author: PostAuthorResponse;
  body: string;
  media: PostMediaResponse[];
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  publishedAt: string | null;
  status: PostStatus | null;
  mine: boolean;
}

export interface PostMediaSpec {
  contentType: 'image/jpeg';
  byteSize: number;
  width: number;
  height: number;
}

/** url 전체는 비밀값이다. 로그에 남기지 않는다. */
export interface PostUploadResponse {
  mediaId: number;
  url: string;
  headers: Record<string, string>;
  expiresAt: string;
}

export interface PostDraftResponse {
  postId: number;
  uploads: PostUploadResponse[];
}

export interface LikeResponse {
  likeCount: number;
  likedByMe: boolean;
}

export interface LikerResponse {
  userId: number;
  nickname: string;
}
