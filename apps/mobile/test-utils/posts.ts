import type { PostMediaResponse, PostResponse } from '@/types/api/post';

export function media(id: number, over: Partial<PostMediaResponse> = {}): PostMediaResponse {
  return {
    id,
    type: 'IMAGE',
    url: `https://media.test/p/${id}/d.webp`,
    thumbnailUrl: `https://media.test/p/${id}/t.webp`,
    width: 1080,
    height: 1350,
    ...over,
  };
}

export function post(over: Partial<PostResponse> = {}): PostResponse {
  return {
    id: 1,
    author: { id: 7, nickname: '달이' },
    body: '오늘 초록 문제 완등',
    media: [media(11)],
    likeCount: 4,
    commentCount: 2,
    likedByMe: false,
    publishedAt: '2026-11-24T02:30:00Z',
    status: null,
    mine: false,
    ...over,
  };
}
