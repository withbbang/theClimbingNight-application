export interface NoticeSummaryResponse {
  id: number;
  title: string;
  pinned: boolean;
  publishedAt: string;
}

export interface NoticeResponse extends NoticeSummaryResponse {
  body: string;
  updatedAt: string;
}

export interface NoticeRequest {
  title: string;
  body: string;
  pinned: boolean;
}
