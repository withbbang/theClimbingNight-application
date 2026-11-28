import type { CommentResponse } from '@/types/api/comment';

export function comment(over: Partial<CommentResponse> = {}): CommentResponse {
  return {
    id: 1,
    parentId: null,
    author: { id: 8, nickname: '바위', profileImageUrl: null },
    body: '첫 댓글이에요',
    replyToNickname: null,
    createdAt: '2026-11-24T02:40:00Z',
    display: 'VISIBLE',
    replyCount: 0,
    mine: false,
    deletable: false,
    ...over,
  };
}
