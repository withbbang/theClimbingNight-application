import type { PostAuthorResponse } from '@/types/api/post';

/** VISIBLE 말고는 자리표시다. author·body는 null이다. HIDDEN_MINE은 내 숨겨진 댓글로 본문이 있다. */
export type CommentDisplay = 'VISIBLE' | 'HIDDEN_MINE' | 'DELETED' | 'HIDDEN';

export interface CommentResponse {
  id: number;
  parentId: number | null;
  author: PostAuthorResponse | null;
  body: string | null;
  /** 답글에만 있다. 대상이 탈퇴했으면 null. */
  replyToNickname: string | null;
  createdAt: string;
  display: CommentDisplay;
  replyCount: number;
  mine: boolean;
  deletable: boolean;
}
