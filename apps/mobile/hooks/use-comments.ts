import { useCursorQuery } from '@/hooks/use-cursor-query';
import { fetchComments } from '@/lib/comment-api';

export const COMMENTS_PAGE_SIZE = 20;

/** 게시물의 최상위 댓글, 오래된 순. 좋아요 캐시 갱신 범위(['posts', …]의 글 키) 밖의 키다. */
export function useComments(postId: number, enabled: boolean) {
  return useCursorQuery(
    ['posts', postId, 'comments'],
    (cursor) => fetchComments(postId, { cursor, size: COMMENTS_PAGE_SIZE }),
    enabled,
  );
}

/** 댓글 하나의 답글, 오래된 순. 펼칠 때만 받는다. */
export function useReplies(postId: number, parentId: number, enabled: boolean) {
  return useCursorQuery(
    ['posts', postId, 'comments', parentId],
    (cursor) => fetchComments(postId, { parentId, cursor, size: COMMENTS_PAGE_SIZE }),
    enabled,
  );
}
