/** 진행 중 표시. 앱과 같은 홀드 세 개를 차례로 짚는다. */
export function PulseHolds() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex h-12 items-center gap-2">
      <span className="hold pulse-hold size-3" />
      <span className="hold pulse-hold size-3 [animation-delay:120ms]" />
      <span className="hold pulse-hold size-3 [animation-delay:240ms]" />
    </div>
  );
}
