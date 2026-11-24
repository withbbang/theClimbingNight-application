export type MembershipRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface MembershipRequestResponse {
  id: number;
  somoimNickname: string;
  status: MembershipRequestStatus;
  rejectReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
}
