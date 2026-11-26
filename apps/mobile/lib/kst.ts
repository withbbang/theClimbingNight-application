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

/** 서울 자정의 시각(ms). */
export function startOfDay(key: DateKey): number {
  return toUtc(key).getTime() - OFFSET_MS;
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

/** 지난해까지의 날에는 연도를 붙인다. */
export function formatDay(key: DateKey, today: DateKey = todayKst()): string {
  const [year, month, day] = key.split('-').map(Number);
  const prefix = year < Number(today.slice(0, 4)) ? `${year}년 ` : '';
  return `${prefix}${month}월 ${day}일(${WEEKDAYS[dayOfWeek(key)]})`;
}

export function formatTime(iso: string): string {
  const date = shifted(new Date(iso));
  return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

export function formatDateTime(iso: string): string {
  return `${formatDay(toDateKey(iso))} ${formatTime(iso)}`;
}

/** '1930', '9:30'처럼 적은 시각을 'HH:MM'으로 바꾼다. 시각이 아니면 null. */
export function parseTime(text: string): string | null {
  const match = /^(\d{1,2}):?(\d{2})$/.exec(text.trim());
  if (!match) {
    return null;
  }
  const [hour, minute] = [Number(match[1]), Number(match[2])];
  return hour < 24 && minute < 60 ? `${pad(hour)}:${pad(minute)}` : null;
}

/** 서울 날짜와 'HH:MM'을 UTC ISO 문자열로 바꾼다. */
export function toIso(day: DateKey, time: string): string {
  const [hour, minute] = time.split(':').map(Number);
  return new Date(startOfDay(day) + (hour * 60 + minute) * 60_000).toISOString();
}

/** 지난 시간. 1분 전까지는 '방금', 일주일이 지나면 서울 날짜로 적는다. */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const elapsed = now.getTime() - Date.parse(iso);
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) {
    return '방금';
  }
  if (minutes < 60) {
    return `${minutes}분 전`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}시간 전`;
  }
  const days = Math.floor(hours / 24);
  return days < 7 ? `${days}일 전` : formatDay(toDateKey(iso), todayKst(now));
}
