import { useState } from 'react';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useSubmit } from '@/hooks/use-submit';
import { isDisconnected, mayBeDone } from '@/lib/error-message';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { createDraft, publishPost } from '@/lib/post-api';
import { uploadPhotos, type PreparedPhoto } from '@/lib/upload-photos';

export interface UploadProgress {
  done: number;
  total: number;
}

interface NewPost {
  photos: PreparedPhoto[];
  body: string;
}

/** 초안을 만들고 → 사진을 R2에 올리고 → 게시한다. 사진 하나라도 못 올리면 게시하지 않는다. */
export function useCreatePost() {
  const client = useQueryClient();
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  // 게시 요청을 보냈는데 답을 못 받았다. 다시 보내면 같은 글이 둘이 될 수 있다.
  const [mayBePosted, setMayBePosted] = useState(false);
  const mutation = useMutation({
    mutationFn: async ({ photos, body }: NewPost) => {
      setMayBePosted(false);
      setProgress({ done: 0, total: photos.length });
      const draft = await createDraft(
        photos.map(({ contentType, byteSize, width, height }) => ({
          contentType,
          byteSize,
          width,
          height,
        })),
      );
      await uploadPhotos(draft.uploads, photos, (done, total) => setProgress({ done, total }));
      try {
        return await publishPost(draft.postId, body);
      } catch (error) {
        setMayBePosted(mayBeDone(error) || isDisconnected(error));
        throw error;
      }
    },
    onSuccess: notifySuccess,
    onError: notifyError,
    onSettled: () => {
      setProgress(null);
      // 게시 응답을 못 받았어도 서버는 게시했을 수 있으니 실패해도 다시 받는다.
      void client.invalidateQueries({ queryKey: ['posts', 'mine', 'in-progress'] });
    },
  });
  const send = useSubmit(mutation);
  return {
    submit: (photos: PreparedPhoto[], body: string) => send({ photos, body }),
    progress,
    error: mutation.error,
    mayBePosted: mutation.isError && mayBePosted,
    /** 입력을 바꾸면 지난 오류를 지운다. 게시됐을 수 있으면 지우지 않는다. */
    clearError: () => {
      if (mutation.isError && !mayBePosted) {
        mutation.reset();
      }
    },
    isPending: mutation.isPending,
    isSuccess: mutation.isSuccess,
  };
}
