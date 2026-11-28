import { apiFetch } from '@/lib/api';
import type { CursorResponse } from '@/types/api/common';
import type {
  GridPostResponse,
  ProfileImageStatusResponse,
  ProfileImageUploadResponse,
  ProfileResponse,
} from '@/types/api/profile';

export const PROFILE_POSTS_PAGE_SIZE = 18;

export const profileApi = {
  profile(id: number) {
    return apiFetch<ProfileResponse>(`/users/${id}/profile`);
  },
  posts(id: number, cursor?: string | null) {
    const after = cursor ? `&cursor=${encodeURIComponent(cursor)}` : '';
    return apiFetch<CursorResponse<GridPostResponse>>(
      `/users/${id}/posts?size=${PROFILE_POSTS_PAGE_SIZE}${after}`,
    );
  },
  updateBio(bio: string) {
    return apiFetch<ProfileResponse>('/users/me/profile', { method: 'PATCH', body: { bio } });
  },
  startImage(byteSize: number) {
    return apiFetch<ProfileImageUploadResponse>('/users/me/profile-images', {
      method: 'POST',
      body: { byteSize },
    });
  },
  confirmImage(id: number) {
    return apiFetch<ProfileImageStatusResponse>(`/users/me/profile-images/${id}/confirm`, {
      method: 'POST',
    });
  },
  imageStatus(id: number) {
    return apiFetch<ProfileImageStatusResponse>(`/users/me/profile-images/${id}`);
  },
  resetImage() {
    return apiFetch<void>('/users/me/profile-image', { method: 'DELETE' });
  },
};
