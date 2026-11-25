/** 오래 걸릴 때만 보인다. JS 없이도 뜨도록 CSS로 늦추고, 빈 주소로 지금 페이지를 다시 연다. */
export function SlowHint() {
  return (
    <p className="slow-hint mt-4 text-muted">
      오래 걸리고 있어요.{' '}
      <a
        href=""
        className="font-semibold text-text underline decoration-accent decoration-2 underline-offset-4"
      >
        다시 열기
      </a>
    </p>
  );
}
