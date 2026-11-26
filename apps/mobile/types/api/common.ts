export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** 커서 방식 목록. nextCursor가 null이면 끝이다. */
export interface CursorResponse<T> {
  items: T[];
  nextCursor: string | null;
}
