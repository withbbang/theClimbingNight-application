import { useEffect, useState } from 'react';

/** setTimeout이 다룰 수 있는 가장 긴 시간. */
const MAX_DELAY_MS = 2 ** 31 - 1;

/** 주어진 시각 중 다음 것이 지나면 다시 그린다. */
export function useNow(boundaries: number[]): number {
  const [now, setNow] = useState(() => Date.now());
  const next = Math.min(...boundaries.filter((time) => time > now));

  useEffect(() => {
    const delay = Math.max(0, next - Date.now());
    if (delay > MAX_DELAY_MS) {
      return;
    }
    // 타이머가 조금 일찍 울려도 경계를 지난 것으로 본다.
    const timer = setTimeout(() => setNow(Math.max(Date.now(), next)), delay);
    return () => clearTimeout(timer);
  }, [next]);

  return now;
}
