import { useEffect, useRef } from 'react';

/** 화면을 떠났는지 알려 준다. 늦게 온 응답이 다른 화면을 움직이지 않게 한다. */
export function useLeft() {
  const left = useRef(false);
  useEffect(() => {
    left.current = false;
    return () => {
      left.current = true;
    };
  }, []);
  return left;
}
