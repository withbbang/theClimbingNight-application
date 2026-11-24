import { apiFetch } from '@/lib/api';
import type { NotificationSettingResponse } from '@/types/api/notification-setting';

export const notificationSettingApi = {
  getMine() {
    return apiFetch<NotificationSettingResponse>('/notification-settings/me');
  },
  update(settings: NotificationSettingResponse) {
    return apiFetch<NotificationSettingResponse>('/notification-settings/me', {
      method: 'PUT',
      body: settings,
    });
  },
};
