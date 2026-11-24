import { apiFetch } from '@/lib/api';
import type { AppSettingsResponse } from '@/types/api/app-setting';

export const appSettingApi = {
  get() {
    return apiFetch<AppSettingsResponse>('/app-settings');
  },
};
