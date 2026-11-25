const OFFSET_MS = 9 * 60 * 60 * 1000;

/** 서울 날짜. 서울은 UTC+9 고정이라 기기 시간대와 Intl을 쓰지 않는다. */
export function formatDate(iso: string): string {
  const date = new Date(new Date(iso).getTime() + OFFSET_MS);
  return `${date.getUTCFullYear()}년 ${date.getUTCMonth() + 1}월 ${date.getUTCDate()}일`;
}
