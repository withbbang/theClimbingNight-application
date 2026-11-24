import { useEffect, useState } from 'react';
import { AppState } from 'react-native';

import { addDays, startOfDay, todayKst } from '@/lib/kst';

/** setTimeout이 다룰 수 있는 가장 긴 시간. */
const MAX_DELAY_MS = 2 ** 31 - 1;

/** 주어진 시각 중 다음 것이 지나거나 앱으로 돌아오면 다시 그린다. 함수를 주면 지금 시각으로 다음 시각을 정한다. */
export function useNow(boundaries: number[] | ((now: number) => number[])): number {
  const [now, setNow] = useState(() => Date.now());
  const times = typeof boundaries === 'function' ? boundaries(now) : boundaries;
  const next = Math.min(...times.filter((time) => time > now));

  useEffect(() => {
    const delay = Math.max(0, next - Date.now());
    if (delay > MAX_DELAY_MS) {
      return;
    }
    // 타이머가 조금 일찍 울려도 경계를 지난 것으로 본다.
    const timer = setTimeout(() => setNow(Math.max(Date.now(), next)), delay);
    return () => clearTimeout(timer);
  }, [next]);

  // 앱이 뒤에 있는 동안에는 타이머가 멈출 수 있다.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        setNow(Date.now());
      }
    });
    return () => subscription.remove();
  }, []);

  return now;
}

/** 서울 날짜. 자정이 지나면 다시 그린다. */
export function useToday() {
  const now = useNow((time) => [startOfDay(addDays(todayKst(new Date(time)), 1))]);
  return todayKst(new Date(now));
}
