# 벽헤는밤 웹

Next.js App Router. 앱 웹뷰와 브라우저에서 함께 쓰는 로그인 없는 읽기 전용 페이지(`/app/*`)다. 관리자 페이지는 3단계에서 만든다.

```bash
pnpm install
pnpm dev     # http://localhost:3000, /api는 API_ORIGIN(기본 http://localhost:8080)으로 보낸다
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

## 문안

소개·굿즈·약관 내용은 `content/`에 있다. 아직 정하지 않은 값은 `[확인 필요: 무엇]`으로 적는다. 남아 있으면 배포하지 않는다.

```bash
grep -rn '\[확인 필요' content
```

## 글꼴

Pretendard는 `pretendard` 패키지의 나눈 woff2를 함께 빌드한다 (SIL OFL 1.1).
