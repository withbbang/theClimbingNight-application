const HOLDS = [
  'left-[4%] top-[80%] h-7 w-12 -rotate-12 [--i:0]',
  'left-[26%] top-[64%] size-9 rotate-12 [--i:1]',
  'left-[14%] top-[34%] h-6 w-10 rotate-6 [--i:2]',
  'left-[46%] top-[44%] h-8 w-14 -rotate-12 [--i:3]',
  'left-[40%] top-[10%] size-8 rotate-45 [--i:4]',
  'left-[70%] top-[24%] h-7 w-11 -rotate-6 [--i:5]',
  'left-[84%] top-[2%] h-9 w-14 rotate-12 [--i:6]',
];

const STARS = [
  'left-[6%] top-[12%] size-2 [--i:0]',
  'left-[30%] top-[22%] size-1 [--i:2]',
  'left-[60%] top-[6%] size-1.5 [--i:4]',
  'left-[92%] top-[46%] size-2 [--i:1]',
  'left-[66%] top-[74%] size-1.5 [--i:3]',
  'left-[86%] top-[86%] size-1 [--i:5]',
];

/** 앱 로그인 화면의 벽을 옮긴 것. 홀드가 아래에서 위로 켜지고 빛이 길을 따라 흐른다. */
export function Wall() {
  return (
    <div aria-hidden className="relative h-56 sm:h-72">
      {STARS.map((star) => (
        <span key={star} className={`star absolute rounded-full bg-border ${star}`} />
      ))}
      {HOLDS.map((hold) => (
        <span key={hold} className={`absolute ${hold}`}>
          <span className="wall-hold hold size-full" />
        </span>
      ))}
    </div>
  );
}
