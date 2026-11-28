import { useMutation, useQueryClient } from '@tanstack/react-query';

import { profileKey } from '@/hooks/use-user-profile';
import { useSession } from '@/hooks/use-session';
import { useSubmit } from '@/hooks/use-submit';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { profileApi } from '@/lib/profile-api';

/** 자기소개를 저장한다. 빈 문자열이면 지운다. */
export function useUpdateBio() {
  const client = useQueryClient();
  const session = useSession();
  const myId = session.status === 'authenticated' ? session.tokens.user.id : null;
  const mutation = useMutation({
    mutationFn: (bio: string) => profileApi.updateBio(bio),
    onSuccess: (profile) => {
      notifySuccess();
      if (myId !== null) {
        client.setQueryData(profileKey(myId), profile);
      }
    },
    onError: notifyError,
  });
  return { ...mutation, submit: useSubmit(mutation) };
}
