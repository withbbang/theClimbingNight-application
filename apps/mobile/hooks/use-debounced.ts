import { useEffect, useState } from 'react';

/** 입력이 잠깐 멈춘 뒤의 값. */
export function useDebounced<T>(value: T, ms = 300): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), ms);
    return () => clearTimeout(timer);
  }, [ms, value]);
  return settled;
}
