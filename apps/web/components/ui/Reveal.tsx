import type { ReactNode } from 'react';

const DELAYS = [
  '',
  '[animation-delay:60ms]',
  '[animation-delay:120ms]',
  '[animation-delay:180ms]',
  '[animation-delay:240ms]',
  '[animation-delay:300ms]',
];

interface RevealProps {
  children: ReactNode;
  /** 이어서 나타날 순서. */
  index?: number;
  className?: string;
}

export function Reveal({ children, index = 0, className = '' }: RevealProps) {
  const delay = DELAYS[Math.min(index, DELAYS.length - 1)];
  return <div className={`reveal ${delay} ${className}`}>{children}</div>;
}
