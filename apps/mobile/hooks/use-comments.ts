import {
  useMutation,
  useQueryClient,
  type InfiniteData,
  type Query,
  type QueryClient,
} from '@tanstack/react-query';

import { useCursorQuery } from '@/hooks/use-cursor-query';
import { useSubmit } from '@/hooks/use-submit';
import { createComment, deleteComment, fetchComments } from '@/lib/comment-api';
import { notifyError, notifySuccess } from '@/lib/haptics';
import type { CommentResponse } from '@/types/api/comment';
import type { CursorResponse } from '@/types/api/common';

type CommentPages = InfiniteData<CursorResponse<CommentResponse>, string | null>;

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

/** 글 캐시(피드 ['posts'], 상세 ['posts', 글 id])만 고른다. 댓글·좋아요 목록은 제외한다. */
function holdsPosts({ queryKey }: Query) {
  return (
    queryKey[0] === 'posts' &&
    (queryKey.length === 1 || (queryKey.length === 2 && typeof queryKey[1] === 'number'))
  );
}

/** 댓글 수는 서버가 센 값으로 맞춘다. */
function refreshCounts(client: QueryClient) {
  return client.invalidateQueries({ predicate: holdsPosts });
}

/** 마지막 쪽이 끝이면 거기에 붙이고, 아직 더 있으면 서버에서 다시 받는다. 받아 둔 적 없으면 아무것도 안 한다. */
function append(client: QueryClient, key: readonly unknown[], created: CommentResponse) {
  const cached = client.getQueryData<CommentPages>(key);
  if (!cached) {
    return;
  }
  const last = cached.pages.length - 1;
  if (cached.pages[last].nextCursor !== null) {
    void client.invalidateQueries({ queryKey: key, exact: true });
    return;
  }
  client.setQueryData<CommentPages>(key, {
    ...cached,
    pages: cached.pages.map((page, i) =>
      i === last ? { ...page, items: [...page.items, created] } : page,
    ),
  });
}

/** 최상위 댓글은 목록 끝에, 답글은 부모의 답글 목록 끝에 붙이고 부모의 답글 수를 올린다. */
export function useCreateComment(postId: number) {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: { body: string; replyToCommentId: number | null }) =>
      createComment(postId, request),
    onSuccess: (created) => {
      const top = ['posts', postId, 'comments'] as const;
      if (created.parentId === null) {
        append(client, top, created);
      } else {
        const parentId = created.parentId;
        append(client, [...top, parentId], created);
        client.setQueryData<CommentPages>(
          top,
          (data) =>
            data && {
              ...data,
              pages: data.pages.map((page) => ({
                ...page,
                items: page.items.map((item) =>
                  item.id === parentId ? { ...item, replyCount: item.replyCount + 1 } : item,
                ),
              })),
            },
        );
      }
      notifySuccess();
    },
    onError: notifyError,
    onSettled: () => void refreshCounts(client),
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

/** 지우면 댓글·답글 목록과 글의 댓글 수를 서버 값으로 다시 맞춘다. */
export function useDeleteComment(postId: number) {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: (id: number) => deleteComment(id).then(() => true),
    onSuccess: notifySuccess,
    onError: notifyError,
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['posts', postId, 'comments'] });
      void refreshCounts(client);
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
