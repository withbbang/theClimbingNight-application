import type { PostResponse } from '@/types/api/post';

export type ReportTargetType = 'POST' | 'COMMENT' | 'USER';
export type ReportReason = 'SPAM' | 'SEXUAL' | 'ABUSE' | 'PORTRAIT_PRIVACY' | 'OTHER';
export type ReportTargetAction = 'HIDE' | 'RESTORE' | 'DISMISS';

/** 탈퇴한 사용자는 nickname이 null이다. */
export interface BlockedUserResponse {
  userId: number;
  nickname: string | null;
  blockedAt: string;
}

/** OTHER면 detail이 필요하다. (500자 이내) */
export interface ReportCreateRequest {
  targetType: ReportTargetType;
  targetId: number;
  reason: ReportReason;
  detail: string | null;
}

/**
 * 열린 신고를 대상별로 묶은 운영진용 항목. post는 지워지지 않은 POST 대상에만, userNickname은 USER 대상에만 있다.
 * targetStatus는 게시물이면 PostStatus, 사용자면 UserStatus, 지워졌으면 DELETED다.
 * quarantineUrls는 숨긴 글의 격리본 주소(5분)다. cleanupFailed면 공개 사본이 남아 있을 수 있다.
 */
export interface ReportTargetResponse {
  targetType: ReportTargetType;
  targetId: number;
  targetStatus: string;
  reportCount: number;
  reasons: Partial<Record<ReportReason, number>>;
  details: string[];
  lastReportedAt: string;
  post: PostResponse | null;
  quarantineUrls: string[];
  userNickname: string | null;
  cleanupFailed: boolean;
}
