# 1단계-07 웹 페이지와 앱 웹뷰 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공지·소개·굿즈·약관 페이지를 `apps/web`에 만들고 앱에서 웹뷰로 연다.

**Architecture:** 웹은 Next.js App Router의 `/app/*` 공개 페이지다. 데이터는 클라이언트 React Query가 같은 주소의 `/api/*`로 받는다. 앱은 `WebPage` 컴포넌트 하나로 모든 페이지를 열고, 우리 `/app/*`가 아닌 주소는 시스템 브라우저로 넘긴다. API는 바꾸지 않는다.

**Tech Stack:** Next 16.4.6, React 19.3.1, TypeScript 6.0.3, ESLint 9, Tailwind 4.3.5, React Query 5, react-markdown 10 + remark-gfm 4 + remark-breaks, Vitest 5 + Testing Library. 앱은 `react-native-webview` 14.0.1 (SDK 58 고정 버전).

**Spec:** `docs/architecture.md` 5·9·11(굿즈)·12장. 규약은 `.claude/rules/{web,mobile,testing}.md`, `api-conventions` 스킬.

**이 계획에 없는 것:** Docker·nginx·운영 주소(계획 8), 관리자 웹(3단계), 로그인이 필요한 웹 페이지.

## 작업 방식

- 브랜치: `dev`에서 `feat/web-pages`. 첫 커밋은 이 문서(`Docs : 웹과 웹뷰 구현 계획 추가`). 커밋 전 `.gitmessage.txt`를 읽고, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 태스크마다 실패하는 테스트 → 실패 확인 → 구현 → `pnpm lint && pnpm typecheck && pnpm test`(웹은 `pnpm build`도) → 커밋.
- 화면 태스크(2~4, 6)는 `frontend-design`, `ui-ux-pro-max`, `taste-skill` 스킬을 먼저 불러온다. **애니메이션을 최대한 많이** 넣는다(사용자 요청).
- `.env.example`은 그 파일만 다루는 명령으로 고친다.
- 리뷰 강도는 `medium`을 제안한다(웹뷰가 여는 주소를 판정함). 실행 전 승인받는다.

## Global Constraints

- 웹뷰 페이지는 로그인 없는 읽기 전용이다. 앱 토큰을 웹뷰에 넘기지 않는다.
- 웹뷰와 앱이 주고받을 메시지가 아직 없으므로 `types/bridge.ts`, `onMessage`, `injectedJavaScript`를 만들지 않는다.
- 데이터 호출은 `lib/api.ts`의 `apiFetch`와 `/api/v1` 상대 경로로만 한다. 서버 컴포넌트는 데이터를 조회하지 않는다.
- 웹 색은 `apps/mobile/lib/theme.ts`의 라이트·다크 값을 그대로 CSS 변수로 옮기고 `prefers-color-scheme`을 따른다. 글꼴은 Pretendard를 자체 제공한다.
- 애니메이션은 CSS `transform`·`opacity`만. `prefers-reduced-motion`이면 끝 상태로 둔다.
- 글자 크기는 `rem`. iOS 웹뷰는 `@supports (-webkit-touch-callout: none) { html { font: -apple-system-body } }` 뒤에 글꼴 패밀리를 다시 지정해 시스템 글자 크기를 따른다.
- 날짜는 UTC+9로 직접 계산한다. `Intl`과 기기 시간대를 쓰지 않는다.
- 사용자가 아직 주지 않은 문안은 `[확인 필요: 무엇]`으로 적는다. 사실을 지어내지 않는다.

## Review Focus

1. **공지 본문에 HTML·`javascript:` 링크가 있다** → 실행되지 않고 글자로 남거나 사라진다. Task 2.
2. **공지 줄바꿈을 한 번만 쳤다(휴대폰에서 씀)** → 줄이 나뉘어 보인다. Task 2.
3. **웹뷰에서 비슷한 주소(`https://api.test.evil.com`, `https://api.test@evil.com`)나 `intent:` 링크를 누른다** → 앱 안에서 열리지 않는다. Task 5.
4. **오프라인이거나 서버가 502다** → 빈 흰 화면 대신 안내와 "다시 시도"가 나온다. Task 5.
5. **삭제된 공지를 푸시로 연다** → "없는 공지" 안내와 목록으로 가는 길이 있다. Task 2·6.

---

### Task 1: 웹 프로젝트와 공용 기반

**Files (`apps/web`):**
- Create: `package.json`, `pnpm-workspace.yaml`(설치 스크립트 허용 목록), `pnpm-lock.yaml`, `tsconfig.json`, `next.config.ts`, `eslint.config.mjs`, `postcss.config.mjs`, `vitest.config.ts`, `vitest.setup.ts`, `.env.example`, `README.md`
- Create: `app/{layout,page,not-found}.tsx`, `app/globals.css`, `app/providers.tsx`, `lib/{api,api-error,kst}.ts`, `types/api/{common,notice,app-setting}.ts`, `components/ui/{QueryState,Reveal,ExternalLink}.tsx`, `public/fonts/`
- Test: `lib/__tests__/{api,kst}.test.ts`, `components/ui/__tests__/*.test.tsx`

**Interfaces — Produces:**
- scripts: `dev`(`next dev`), `build`, `start`, `lint`(`eslint .`), `typecheck`(`tsc --noEmit`), `test`(`vitest run`)
- `apiFetch<T>(path: string): Promise<T>` — `/api/v1${path}` GET, 15초 제한. `ApiError(code, message, status)`, `isNotFound(error)`는 앱과 같은 코드·문구
- `formatDate(iso: string): string` → `2026년 11월 24일`
- `<QueryState query>{(data) => …}</QueryState>` — 앱과 같은 규칙, 실패하면 "다시 시도" 버튼
- `<Reveal index>` — 순서대로 나타나는 CSS 애니메이션 / `<ExternalLink href>` — `target="_blank" rel="noopener noreferrer"`
- 타입은 앱의 `types/api/{common,notice,app-setting}.ts`와 같은 이름·필드

**결정:** `next.config.ts`는 `output: 'standalone'`, `poweredByHeader: false`, rewrite `/api/:path*` → `${API_ORIGIN ?? 'http://localhost:8080'}/api/:path*`(개발용, 운영은 nginx), `allowedDevOrigins: ['10.0.2.2']`, 전체 헤더 `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Content-Security-Policy: frame-ancestors 'none'`. `/`는 `/app/about`으로 redirect.

- [ ] **Step 1: 실패하는 테스트**

```ts
it('calls the same origin', ...)              // fetch 첫 인자 '/api/v1/notices/1'
it('throws ApiError with the server message', ...)
it('reports an unreadable 502 as CLIENT_BAD_RESPONSE', ...)
it('reports a timeout after 15 seconds', ...)  // vi.useFakeTimers
it('uses the Seoul date', () => {
  expect(formatDate('2026-12-31T14:59:59Z')).toBe('2026년 12월 31일');
  expect(formatDate('2026-12-31T15:00:00Z')).toBe('2027년 1월 1일');
});
it('keeps data when a refetch fails', ...)     // QueryState
it('retries from the error state', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test`.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test && pnpm build`.
- [ ] **Step 5: 커밋** `Feat(web) : 웹 프로젝트 기반 추가`

---

### Task 2: 공지 목록과 상세

**Files (`apps/web`):** Create: `app/app/notices/page.tsx`, `app/app/notices/[id]/page.tsx`, `components/notice/{NoticeList,NoticeDetail,Markdown}.tsx`, `hooks/use-notices.ts`, `lib/notice-api.ts`, `lib/parse-id.ts`. Test: `components/notice/__tests__/*.test.tsx`

**Interfaces:**
- Consumes: Task 1 전부
- Produces: `/app/notices`, `/app/notices/{id}`, `useNotices()`(20개씩 `useInfiniteQuery`, 키 `['notices']`), `useNotice(id)`(키 `['notices', id]`), `<Markdown>{string}</Markdown>`

**결정:** `rehype-raw`를 쓰지 않는다. 링크는 모두 `ExternalLink`, 이미지는 `https:`만. 제목이 페이지 `h1`이라 본문 제목은 한 단계씩 내린다. 표는 가로로 스크롤한다. 긴 주소는 줄바꿈한다. 쪽이 밀려 겹친 공지는 ID로 한 번만 보인다.

- [ ] **Step 1: 실패하는 테스트**

```ts
it('shows pinned notices with a label', ...)          // '고정된 공지'
it('loads more without repeating a notice', ...)
it('says there are no notices yet', ...)              // '아직 공지가 없어요'
it('does not render raw HTML', ...)                   // <img onerror>, <script> 요소 없음
it('drops javascript: links', ...)
it('keeps single line breaks', ...)                   // 'a\nb' → <br>
it('opens links outside', ...)                        // target _blank
it('shows only https images', ...)
it('offers the list when the notice is gone', ...)    // 404 → '없는 공지예요' + '공지 목록' 링크
it('does not call the API for a bad id', ...)         // 'abc', '0', '-1'
```

- [ ] **Step 2: 실패 확인** — `pnpm test notice`.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test && pnpm build`.
- [ ] **Step 5: 커밋** `Feat(web) : 공지 목록과 상세 추가`

---

### Task 3: 동호회 소개와 굿즈

**Files (`apps/web`):** Create: `app/app/{about,goods}/page.tsx`, `components/about/About.tsx`, `components/goods/Goods.tsx`, `content/{about,goods}.ts`, `hooks/use-app-settings.ts`, `lib/app-setting-api.ts`. Test: `components/{about,goods}/__tests__/*.test.tsx`

**Interfaces — Produces:** `/app/about`, `/app/goods`. `interface GoodsItem { name: string; description: string; image: string; alt: string }`, `GOODS: GoodsItem[]`(처음엔 빈 배열, 이미지는 `public/goods/`). `<Goods items={GOODS} />`로 주입한다.

**결정:** 소개는 확정된 사실(벽헤는밤, 클라이밍 동호회, 가입은 소모임에서 받고 앱에서 인증)만 쓰고 나머지는 `[확인 필요: …]`. 링크는 `somoimJoinUrl`·`smartstoreUrl`이 있을 때만 보인다. 가격은 스마트스토어와 어긋나지 않게 싣지 않는다.

- [ ] **Step 1: 실패하는 테스트**

```ts
it('links to somoim when the URL is set', ...)
it('hides the join link without a URL or when settings fail', ...)  // 소개 글은 남음
it('lists goods with a store link', ...)
it('says goods are being prepared when there are none', ...)        // '굿즈를 준비하고 있어요'
it('keeps the store link when there are no goods', ...)
it('hides the store link without a URL', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test about goods`.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test && pnpm build`.
- [ ] **Step 5: 커밋** `Feat(web) : 동호회 소개와 굿즈 추가`

---

### Task 4: 이용약관과 개인정보처리방침

**Files:** Create (`apps/web`): `app/app/{terms,privacy}/page.tsx`, `components/legal/LegalPage.tsx`, `content/{terms,privacy}.ts`. Modify: `.claude/skills/release/SKILL.md`. Test: `components/legal/__tests__/LegalPage.test.tsx`

**Interfaces — Produces:** `/app/terms`, `/app/privacy`, `/app/privacy#account-deletion`. `interface LegalSection { id: string; title: string; body: string }`(body는 `Markdown`으로 표시), `<LegalPage title effectiveDate sections />`(목차 포함).

**결정:** 초안이며 게시 전 사용자가 확인한다. 개인정보 항목은 추측하지 않고 `apps/api`의 엔티티·탈퇴 처리·푸시 발송 코드를 읽어 실제 수집·삭제·보관·외부 전송(소셜 로그인, Expo Push)대로 쓴다. 시행일·책임자·연락처·보관 기간처럼 코드로 알 수 없는 값은 `[확인 필요: …]`. `#account-deletion`에는 앱 안 탈퇴 경로와 앱 없이 요청하는 방법을 적는다(Google Play의 웹 삭제 안내 주소로 씀). `release` 스킬에 "`grep -rn '\[확인 필요' apps/web/content`가 비어야 배포"를 추가한다.

- [ ] **Step 1: 실패하는 테스트**

```ts
it('links each contents entry to its section', ...)
it('has the sections the stores need', ...)  // privacy id: collected, purpose, retention, processors, rights, account-deletion, contact
it('has unique section ids', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test legal`.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test && pnpm build`.
- [ ] **Step 5: 커밋** `Feat(web) : 약관과 개인정보처리방침 추가`

---

### Task 5: 앱 웹뷰 컴포넌트

**Files (`apps/mobile`):** Create: `components/web/WebPage.tsx`, `lib/web-url.ts`. Modify: `package.json`(`pnpm expo install react-native-webview`), `lib/config.ts`, `jest.setup.ts`(WebView mock), `.env.example`, `README.md`. Test: `lib/__tests__/{web-url,config}.test.ts`, `components/web/__tests__/WebPage.test.tsx`

**Interfaces — Produces:**
- `config.webUrl: string` — `EXPO_PUBLIC_WEB_URL`, 없으면 `apiUrl`. 끝 `/` 제거
- `type WebPath = '/app/notices' | `/app/notices/${number}` | '/app/about' | '/app/goods' | '/app/terms' | '/app/privacy'`
- `webUrl(path: WebPath): string`
- `decideLink(url: string): 'inside' | 'outside' | 'block'` — `inside`는 origin이 같고 경로가 `/app/`으로 시작하며 사용자 정보가 없는 주소, `outside`는 그 밖의 `http(s)`·`mailto`·`tel`, 나머지는 `block`. RN의 `URL` 동작을 설치된 소스로 확인하고, 기기와 Jest가 다르면 정규식으로 파싱한다.
- `<WebPage path title? tab? />` — `title`이 있으면 뒤로 버튼과 네이티브 머리글을 둔다. `tab`이면 뒤로 버튼이 없다.

**결정:** `originWhitelist={['*']}`(라이브러리가 멋대로 외부 앱을 열지 않게)와 `onShouldStartLoadWithRequest`로 직접 판정한다. 최상위 프레임만 외부로 넘기고 하위 프레임의 우리 것이 아닌 주소는 막는다. `setSupportMultipleWindows={false}`, `allowFileAccess={false}`, `javaScriptCanOpenWindowsAutomatically={false}`, `sharedCookiesEnabled={false}`. 뒤로(버튼·Android 하드웨어)는 웹뷰 안 기록이 먼저다. 하드웨어 뒤로는 화면이 포커스일 때만 잡는다. 첫 로딩은 `PulseHolds`, 주 문서의 `onError`·`onHttpError`(≥500)는 네이티브 `Notice`로 바꾸고 "다시 시도"는 웹뷰를 새로 만든다. `onContentProcessDidTerminate`·`onRenderProcessGone`은 새로 만든다. 배경은 테마 색으로 칠해 흰 화면이 번쩍이지 않게 한다.

- [ ] **Step 1: 실패하는 테스트** (`webUrl`은 `https://api.test`)

```ts
it.each([
  ['https://api.test/app/notices/1', 'inside'],
  ['https://api.test/app/about?x=1#y', 'inside'],
  ['https://api.test/api/v1/notices', 'outside'],
  ['https://api.test/app/../api/v1/notices', 'outside'],
  ['https://api.test.evil.com/app/notices', 'outside'],
  ['https://api.test@evil.com/app/notices', 'outside'],
  ['http://api.test/app/notices', 'outside'],
  ['https://smartstore.naver.com/x', 'outside'],
  ['mailto:a@b.c', 'outside'],
  ['intent://x#Intent;end', 'block'],
  ['javascript:alert(1)', 'block'],
  ['file:///etc/hosts', 'block'],
  ['', 'block'],
])('decides %s as %s', ...)
it('uses the API address when there is no web address', ...)
it('opens outside links in the browser and stays', ...)     // Linking.openURL 1회, false 반환
it('does not open the browser for subframes', ...)
it('tells when the browser does not open', ...)             // '링크를 열지 못했어요.'
it('goes back inside the page before leaving', ...)
it('does not take the hardware back on an unfocused screen', ...)
it('replaces a failed page with a retry', ...)              // onError, onHttpError 502
it('keeps the page for a 404', ...)                         // 웹의 안내를 보여 줌
it('recreates the page after its process dies', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test web-url WebPage config`.
- [ ] **Step 3: 구현** — 콜백의 이름·발생 조건은 설치된 `react-native-webview` 소스로 확인한다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test && pnpm expo install --check`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 웹뷰 컴포넌트 추가`

---

### Task 6: 앱 화면 연결

**Files (`apps/mobile`):**
- Create: `app/notices/{index,[id]}.tsx`, `app/about.tsx`, `app/legal/{terms,privacy}.tsx`, `components/auth/LegalLinks.tsx`
- Modify: `app/(tabs)/{goods,index,my}.tsx`, `app/{login,signup}.tsx`, `components/home/RecentNotices.tsx`, `lib/push-route.ts`
- Test: `__tests__/{web-screens,home,my,login,signup}.test.tsx`, `lib/__tests__/push-route.test.ts`

**Interfaces:**
- Consumes: `<WebPage>`, `parseId`, `MenuRow`, `PressCard`
- Produces: 라우트 `/notices`, `/notices/{id}`, `/about`, `/legal/terms`, `/legal/privacy`

**결정:** 홈 공지 줄은 상세로 가고 "공지 모두 보기"를 둔다(공지가 없으면 숨김). 홈의 비회원 영역과 마이 메뉴에 "동호회 소개"를 둔다. 마이 메뉴의 "동호회 소개", "이용약관", "개인정보처리방침"은 로그인하지 않아도 보인다. 로그인·가입 화면에는 "계속하면 이용약관과 개인정보처리방침에 동의하게 돼요"와 두 링크를 둔다. 가입 화면에서 약관을 보고 돌아와도 적던 닉네임이 남는다. 공지 푸시는 `noticeId`가 올바르면 상세, 아니면 `/notices`다.

- [ ] **Step 1: 실패하는 테스트**

```ts
it.each([
  [{ type: 'NOTICE_PUBLISHED', noticeId: 2 }, '/notices/2'],
  [{ type: 'NOTICE_PUBLISHED', noticeId: '../my' }, '/notices'],
  [{ type: 'NOTICE_PUBLISHED' }, '/notices'],
])('routes %p to %s', ...)
it('opens a notice from home', ...)
it('opens all notices from home', ...)
it('hides all notices when there are none', ...)
it('loads the goods page in the tab without a back button', ...)
it('shows a way out for a bad notice id', ...)        // WebView를 만들지 않음
it('shows about and legal menus without login', ...)
it('shows legal links on login and signup', ...)
it('keeps the nickname after reading the terms', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test`.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 공지와 굿즈, 약관 웹뷰 연결`

---

### Task 7: 문서와 화면 확인

**Files:** Modify: `CLAUDE.md`(앱 디렉터리 문장, 웹 명령에 `typecheck`), `.claude/rules/mobile.md`(`WebPage`, `decideLink`), `.claude/rules/web.md`(`content/`, `[확인 필요]` 규칙), `docs/architecture.md`(9장 `/app/*` 경로와 로그인 동의 문구, 13장에 `EXPO_PUBLIC_WEB_URL`)

- [ ] **Step 1: 자동 검사** — 두 앱의 lint·typecheck·test, 웹 build, `pnpm expo install --check`. Expected: 모두 통과.
- [ ] **Step 2: 화면 확인** — 확인용 기기를 따로 띄우고 앱 개발 서버 8090, 웹 3000, 로컬 API로 본다. iOS·Android, 라이트·다크, 동작 줄이기, 가장 큰 글꼴에서 모든 웹뷰 페이지, 외부 링크, 웹뷰 안 뒤로, 서버를 끈 상태의 재시도, 공지 푸시 경로를 확인한다. 웹은 브라우저 375px·1280px도 본다. 끝나면 정리하고, 못 본 화면은 그대로 보고한다.
- [ ] **Step 3: 커밋** `Docs : 웹과 웹뷰 문서 반영`

---

## 이 계획이 끝나면 되는 것

- 1단계 화면이 모두 동작한다. 문안의 `[확인 필요]`는 사용자에게 받아 채운다.
- 계획 8이 이어받는 것: web 이미지(`output: 'standalone'`)와 nginx의 `/api/*`·`/*` 분기, 스토어에 낼 `/app/privacy`·`/app/privacy#account-deletion` 주소, 남은 `[확인 필요]` 채우기.
- 정해 둔 것(제안): 공지 공유 링크의 미리보기 제목은 공통 값(서버에서 조회하지 않음), 굿즈 목록은 코드의 `content/goods.ts`에서 관리, 웹뷰-앱 메시지 없음.
