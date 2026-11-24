export interface PenaltyResponse {
  id: number;
  meetupId: number;
  meetupTitle: string;
  meetupStartsAt: string;
  type: 'LATE_CANCEL' | 'NO_SHOW';
  active: boolean;
  revokeReason: string | null;
}
