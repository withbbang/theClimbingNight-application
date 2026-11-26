import { apiFetch } from '@/lib/api';
import type { PageResponse } from '@/types/api/common';
import type {
  BlockedUserResponse,
  ReportCreateRequest,
  ReportTargetAction,
  ReportTargetResponse,
  ReportTargetType,
} from '@/types/api/moderation';

export const moderationApi = {
  blocks() {
    return apiFetch<BlockedUserResponse[]>('/blocks');
  },
  block(userId: number) {
    return apiFetch<BlockedUserResponse>('/blocks', { method: 'POST', body: { userId } });
  },
  unblock(userId: number) {
    return apiFetch<void>(`/blocks/${userId}`, { method: 'DELETE' });
  },
  report(request: ReportCreateRequest) {
    return apiFetch<void>('/reports', { method: 'POST', body: request });
  },
  targets(page: number, size: number) {
    return apiFetch<PageResponse<ReportTargetResponse>>(
      `/report-targets?page=${page}&size=${size}`,
    );
  },
  act(targetType: ReportTargetType, targetId: number, action: ReportTargetAction) {
    return apiFetch<void>(`/report-targets/${targetType}/${targetId}/actions`, {
      method: 'POST',
      body: { action },
    });
  },
};
