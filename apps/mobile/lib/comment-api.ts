import { apiFetch } from '@/lib/api';
import type { CommentResponse } from '@/types/api/comment';
import type { CursorResponse } from '@/types/api/common';

interface FetchCommentsParams {
  parentId?: number | null;
  cursor?: string | null;
  size: number;
}

export function fetchComments(postId: number, { parentId, cursor, size }: FetchCommentsParams) {
  const parent = parentId == null ? '' : `&parentId=${parentId}`;
  const after = cursor == null ? '' : `&cursor=${encodeURIComponent(cursor)}`;
  return apiFetch<CursorResponse<CommentResponse>>(
    `/posts/${postId}/comments?size=${size}${parent}${after}`,
  );
}

export function createComment(
  postId: number,
  body: { body: string; replyToCommentId: number | null },
) {
  return apiFetch<CommentResponse>(`/posts/${postId}/comments`, { method: 'POST', body });
}

export function deleteComment(id: number) {
  return apiFetch<void>(`/comments/${id}`, { method: 'DELETE' });
}
