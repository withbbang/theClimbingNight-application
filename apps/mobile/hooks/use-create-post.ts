import { useState } from 'react';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useSubmit } from '@/hooks/use-submit';
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
  const mutation = useMutation({
    mutationFn: async ({ photos, body }: NewPost) => {
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
      return publishPost(draft.postId, body);
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
    isPending: mutation.isPending,
    isSuccess: mutation.isSuccess,
  };
}
