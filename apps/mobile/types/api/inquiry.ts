export interface InquiryResponse {
  id: number;
  title: string;
  body: string;
  status: 'OPEN' | 'ANSWERED';
  answer: string | null;
  answeredAt: string | null;
  createdAt: string;
}

export interface InquiryAdminResponse extends InquiryResponse {
  userId: number;
  /** 탈퇴한 문의자는 null. */
  nickname: string | null;
}
