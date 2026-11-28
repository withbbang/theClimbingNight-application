import { useEffect, useRef, useState } from 'react';

import { useQueryClient } from '@tanstack/react-query';
import { launchImageLibraryAsync } from 'expo-image-picker';

import { profileKey } from '@/hooks/use-user-profile';
import { useSession } from '@/hooks/use-session';
import { errorMessage } from '@/lib/error-message';
import { notifyError, notifySuccess } from '@/lib/haptics';
import { profileApi } from '@/lib/profile-api';
import { PhotoError } from '@/lib/upload-photos';
import { prepareProfilePhoto, uploadProfilePhoto } from '@/lib/upload-profile-image';

export type ProfileImagePhase = 'idle' | 'uploading' | 'applying' | 'slow' | 'failed';

export const POLL_INTERVAL_MS = 2_000;
/** 이 시간이 지나도 적용 중이면 더 묻지 않고 "잠시 뒤 반영돼요"로 바꾼다. */
export const POLL_LIMIT_MS = 30_000;
const MAX_POLLS = POLL_LIMIT_MS / POLL_INTERVAL_MS;

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * 프로필 사진 바꾸기·되돌리기. 고르고 → 줄이고 → R2에 올리고 → 확정한 뒤 적용될 때까지 2초마다 최대 30초 묻는다.
 * 화면을 떠나면 묻기를 멈춘다. 끝나면 내 프로필 쿼리만 무효화한다(다른 곳의 아바타는 다음에 받을 때 바뀐다).
 */
export function useProfileImage() {
  const client = useQueryClient();
  const session = useSession();
  const myId = session.status === 'authenticated' ? session.tokens.user.id : null;
  const [phase, setPhase] = useState<ProfileImagePhase>('idle');
  const [preview, setPreview] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const busy = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const refreshProfile = () =>
    myId === null ? Promise.resolve() : client.invalidateQueries({ queryKey: profileKey(myId) });

  function fail(cause: unknown) {
    notifyError();
    if (mounted.current) {
      setError(cause instanceof PhotoError ? cause.message : errorMessage(cause));
      setPhase('idle');
      setPreview(null);
    }
  }

  async function pollUntilDone(imageId: number) {
    for (let i = 0; i < MAX_POLLS; i += 1) {
      await wait(POLL_INTERVAL_MS);
      if (!mounted.current) {
        return;
      }
      const { status } = await profileApi.imageStatus(imageId);
      if (!mounted.current) {
        return;
      }
      if (status === 'READY' || status === 'REMOVED') {
        await refreshProfile();
        if (mounted.current) {
          setPhase('idle');
          setPreview(null);
        }
        return;
      }
      if (status === 'FAILED') {
        notifyError();
        setPhase('failed');
        setPreview(null);
        return;
      }
    }
    setPhase('slow');
  }

  async function pick() {
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      const result = await launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 1,
      });
      if (result.canceled || !mounted.current) {
        return;
      }
      setError(null);
      setPhase('uploading');
      const photo = await prepareProfilePhoto(result.assets[0]);
      if (!mounted.current) {
        return;
      }
      setPreview(photo.uri);
      const upload = await profileApi.startImage(photo.byteSize);
      await uploadProfilePhoto(upload, photo);
      await profileApi.confirmImage(upload.id);
      if (!mounted.current) {
        return;
      }
      setPhase('applying');
      await pollUntilDone(upload.id);
    } catch (cause) {
      fail(cause);
    } finally {
      busy.current = false;
    }
  }

  async function reset() {
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      setError(null);
      await profileApi.resetImage();
      notifySuccess();
      if (mounted.current) {
        setPreview(null);
        setPhase('idle');
      }
      await refreshProfile();
    } catch (cause) {
      fail(cause);
    } finally {
      busy.current = false;
    }
  }

  return { pick, reset, phase, preview, error };
}
