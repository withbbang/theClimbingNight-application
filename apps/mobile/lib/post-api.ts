import { apiFetch } from '@/lib/api';
import type { CursorResponse } from '@/types/api/common';
import type { PostDraftResponse, PostMediaSpec, PostResponse } from '@/types/api/post';

export function fetchFeed(cursor: string | null, size: number) {
  const after = cursor === null ? '' : `&cursor=${encodeURIComponent(cursor)}`;
  return apiFetch<CursorResponse<PostResponse>>(`/posts?size=${size}${after}`);
}

export function fetchPost(id: number) {
  return apiFetch<PostResponse>(`/posts/${id}`);
}

export function fetchMyInProgress() {
  return apiFetch<PostResponse[]>('/posts/mine/in-progress');
}

export function createDraft(media: PostMediaSpec[]) {
  return apiFetch<PostDraftResponse>('/posts', { method: 'POST', body: { media } });
}

export function publishPost(id: number, body: string) {
  return apiFetch<PostResponse>(`/posts/${id}/publish`, { method: 'POST', body: { body } });
}

export function updatePost(id: number, body: string) {
  return apiFetch<PostResponse>(`/posts/${id}`, { method: 'PATCH', body: { body } });
}

export function deletePost(id: number) {
  return apiFetch<void>(`/posts/${id}`, { method: 'DELETE' });
}
