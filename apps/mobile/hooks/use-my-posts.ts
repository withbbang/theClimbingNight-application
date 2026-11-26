import { useMutation, useQuery, useQueryClient, type Query } from '@tanstack/react-query';

import { useRole } from '@/hooks/use-role';
import { useSubmit } from '@/hooks/use-submit';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { deletePost, fetchMyInProgress, fetchPost, updatePost } from '@/lib/post-api';
import type { PostResponse } from '@/types/api/post';

const IN_PROGRESS = ['posts', 'mine', 'in-progress'];
/** 사진을 정리하는 동안 다시 묻는 간격. */
export const PENDING_POLL_MS = 10_000;

export const hasPending = (posts: PostResponse[] | undefined) =>
  posts?.some((post) => post.status === 'PENDING') ?? false;

/** 피드와 게시물 상세. 내 글 목록(['posts', 'mine', …])은 빼고 다시 받는다. */
const isFeed = ({ queryKey }: Query) => queryKey[0] === 'posts' && queryKey[1] !== 'mine';

/** 내가 올리는 중이거나 올리지 못한 글. 정리 중인 글이 있으면 10초마다 다시 묻는다. */
export function useMyInProgress() {
  const role = useRole();
  return useQuery({
    queryKey: IN_PROGRESS,
    queryFn: fetchMyInProgress,
    enabled: role !== null,
    refetchInterval: (query) => (hasPending(query.state.data) ? PENDING_POLL_MS : false),
  });
}

/** 정리가 끝난 글이 피드에 나오도록 피드와 상세를 다시 받는다. */
export function useRefreshFeed() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ predicate: isFeed });
}

export function useDeleteMyPost() {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: deletePost,
    onSuccess: notifySuccess,
    onError: notifyError,
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['posts'] });
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}

/** 고치는 폼용. 화면을 열 때마다 새로 받는다. */
export function usePostForEdit(id: number | null) {
  const role = useRole();
  return useQuery({
    queryKey: ['posts', id],
    queryFn: () => fetchPost(id ?? 0),
    enabled: role !== null && id !== null,
    refetchOnMount: 'always',
  });
}

export function useUpdatePost(id: number) {
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: (body: string) => updatePost(id, body),
    onSuccess: notifySuccess,
    onError: notifyError,
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['posts', id] });
      void client.invalidateQueries({ queryKey: ['posts'], exact: true });
    },
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
