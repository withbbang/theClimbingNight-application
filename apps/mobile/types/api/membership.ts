import type { RosterCandidateResponse } from './roster';
import type { UserResponse } from './user';

export type MembershipRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface MembershipRequestResponse {
  id: number;
  somoimNickname: string;
  status: MembershipRequestStatus;
  rejectReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
}

export interface MembershipReviewItemResponse {
  id: number;
  user: UserResponse;
  somoimNickname: string;
  status: MembershipRequestStatus;
  rejectReason: string | null;
  createdAt: string;
  candidates: RosterCandidateResponse[];
  bulkApprovable: boolean;
}

/** 기존 명부에 잇거나 새로 만들거나 둘 중 하나. */
export type MembershipApproveRequest = { rosterMemberId: number } | { createNew: true };

export interface BulkApproveResponse {
  approvedCount: number;
  skippedCount: number;
}
