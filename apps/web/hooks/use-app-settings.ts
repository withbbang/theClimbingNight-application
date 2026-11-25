import { useQuery } from '@tanstack/react-query';

import { appSettingApi } from '@/lib/app-setting-api';

export function useAppSettings() {
  return useQuery({ queryKey: ['app-settings'], queryFn: appSettingApi.get });
}
