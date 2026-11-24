export interface InquiryResponse {
  id: number;
  title: string;
  body: string;
  status: 'OPEN' | 'ANSWERED';
  answer: string | null;
  answeredAt: string | null;
  createdAt: string;
}
