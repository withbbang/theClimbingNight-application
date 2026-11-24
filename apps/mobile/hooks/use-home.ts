import { useQuery } from '@tanstack/react-query';

import { appSettingApi } from '@/lib/app-setting-api';
import { noticeApi } from '@/lib/notice-api';

const RECENT_NOTICES = 3;

export function useRecentNotices() {
  return useQuery({
    queryKey: ['notices', 'recent'],
    queryFn: () => noticeApi.list(0, RECENT_NOTICES),
  });
}

export function useAppSettings() {
  return useQuery({ queryKey: ['app-settings'], queryFn: appSettingApi.get });
}
