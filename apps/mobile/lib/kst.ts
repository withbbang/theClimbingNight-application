/** 서울 날짜 'YYYY-MM-DD'. 서울은 UTC+9 고정이라 기기 시간대와 Intl을 쓰지 않는다. */
export type DateKey = string;

const OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
export const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;

const pad = (value: number) => String(value).padStart(2, '0');

const toUtc = (key: DateKey) => new Date(`${key}T00:00:00Z`);

const fromUtc = (date: Date): DateKey => date.toISOString().slice(0, 10);

/** UTC 메서드로 읽으면 서울 시각이 나오는 Date. */
const shifted = (date: Date) => new Date(date.getTime() + OFFSET_MS);

export function todayKst(now: Date = new Date()): DateKey {
  return fromUtc(shifted(now));
}

export function toDateKey(iso: string): DateKey {
  return todayKst(new Date(iso));
}

export function addDays(key: DateKey, days: number): DateKey {
  return fromUtc(new Date(toUtc(key).getTime() + days * DAY_MS));
}

/** 말일을 넘으면 그 달의 말일로 맞춘다. */
export function addMonths(key: DateKey, months: number): DateKey {
  const date = toUtc(key);
  const year = date.getUTCFullYear();
  const month = date.getUTCMonth() + months;
  const last = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return fromUtc(new Date(Date.UTC(year, month, Math.min(date.getUTCDate(), last))));
}

export function monthStart(key: DateKey): DateKey {
  return `${key.slice(0, 7)}-01`;
}

export function monthEnd(key: DateKey): DateKey {
  return addDays(addMonths(monthStart(key), 1), -1);
}

export function dayOfWeek(key: DateKey): number {
  return toUtc(key).getUTCDay();
}

/** 일요일에 시작하는 온전한 주들. 앞뒤 달의 날로 채운다. */
export function monthGrid(key: DateKey): DateKey[][] {
  const first = addDays(monthStart(key), -dayOfWeek(monthStart(key)));
  const end = monthEnd(key);
  const weeks: DateKey[][] = [];
  for (let start = first; start <= end; start = addDays(start, 7)) {
    weeks.push(Array.from({ length: 7 }, (_, i) => addDays(start, i)));
  }
  return weeks;
}

export function formatDay(key: DateKey): string {
  const [, month, day] = key.split('-').map(Number);
  return `${month}월 ${day}일(${WEEKDAYS[dayOfWeek(key)]})`;
}

export function formatTime(iso: string): string {
  const date = shifted(new Date(iso));
  return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

export function formatDateTime(iso: string): string {
  return `${formatDay(toDateKey(iso))} ${formatTime(iso)}`;
}
