# 1단계-05 모바일 앱 기반 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `apps/mobile`에 Expo 앱을 만들고 소셜 로그인 4종, 닉네임 가입, 토큰 저장·갱신, API 클라이언트, 탭 구조, 푸시 토큰 등록까지 development build에서 동작하게 한다.

**Architecture:** 세션은 React 밖의 `lib/session-store.ts`가 들고 `expo-secure-store`에 저장한다. 모든 API 호출은 `lib/api.ts`의 `apiFetch`를 거치고, 401 `AUTH_REQUIRED`일 때만 single-flight로 토큰을 갱신해 한 번 재시도한다. 탭은 비로그인도 열 수 있고, 로그인이 필요한 화면만 로그인 화면으로 보낸다. 소셜 SDK는 `lib/social/` 뒤에 숨겨 화면과 테스트가 SDK를 직접 알지 않게 한다.

**Tech Stack:** Expo SDK 58 (`expo@~58.0.26`, React Native 0.88.1, React 19.3.0, TypeScript ~6.0.3), Expo Router 58, React Query 5, Reanimated 4.7.1, `@react-native-kakao/core`·`user` 3.2.0, `@react-native-seoul/naver-login` 5.0.1, `@react-native-google-signin/google-signin` 17.0.0, `expo-apple-authentication`, `expo-notifications`, jest-expo, `@testing-library/react-native` 14.

**Spec:** `docs/architecture.md` 6장 "가입과 로그인", 9장 화면 구성, 11장 "푸시 알림", 12장 심사 체크리스트. 규약은 `.claude/rules/mobile.md`, `.claude/rules/testing.md`, `api-conventions` 스킬.

패키지 버전과 API는 2026-11-24에 npm 배포본의 타입 선언과 config plugin 소스를 읽어 확인했다. 설치된 버전이 다르면 `node_modules`의 타입이 우선이다.

## 사용자가 준비할 것

코드와 단위 테스트(Task 1~8)는 이것 없이 진행할 수 있다. 실제 로그인·푸시 확인(Task 9)에는 필요하다.

| 항목 | 쓰는 곳 |
|---|---|
| 앱 식별자 확정. 이 계획은 `com.theclimbingnight.app`으로 쓴다 (제안) | Task 1 전. 스토어 등록 뒤에는 바꿀 수 없다 |
| Expo 계정, EAS 프로젝트 ID | `EAS_PROJECT_ID` |
| 카카오 앱 (네이티브 앱 키, iOS 번들 ID, Android 키 해시) | `EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY`, API의 `KAKAO_APP_ID` |
| 네이버 앱 (Client ID·Secret, URL scheme `theclimbingnightnaver`) | `EXPO_PUBLIC_NAVER_CLIENT_ID`·`_SECRET` |
| 구글 OAuth 클라이언트 3개 (Web, iOS, Android SHA-1) | `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`·`_IOS_CLIENT_ID`, API의 `GOOGLE_CLIENT_IDS`(Web·iOS 둘 다) |
| Apple 개발자 계정 (Sign in with Apple, Push) | API의 `APPLE_AUDIENCES=com.theclimbingnight.app` |
| Firebase 프로젝트의 `google-services.json` + EAS에 FCM V1 키 등록 | `GOOGLE_SERVICES_JSON` (Android 푸시) |

`.env` 파일은 사용자가 `.env.example`을 복사해 채운다 (에이전트는 읽거나 쓰지 않는다).

## 작업 방식

- 브랜치: `dev`에서 `feat/mobile-foundation`을 딴다. 태스크마다 커밋한다.
- 커밋: `.gitmessage.txt` 정책. `Feat(mobile) : 제목` (50자 이내), 본문 `-` 목록, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 메시지에 `.env` 글자가 들어가면 파일로 써서 `git commit -F`.
- 모든 명령은 `apps/mobile`에서 실행한다. 태스크 완료 기준은 `pnpm lint && pnpm typecheck && pnpm test` 통과.
- 화면 태스크(4, 5, 7)는 시작할 때 `frontend-design`, `ui-ux-pro-max`, `taste-skill` 스킬을 불러온다. 사용자는 **애니메이션을 최대한 많이** 원하므로 스킬의 "모션 절제" 기본값보다 이 요청이 우선한다. `taste-skill`은 웹 랜딩용이라 네이티브에 맞는 부분(상태 화면, 대비, 카피 점검)만 쓴다.
- 모든 태스크가 끝나면 CLAUDE.md 절차(`/code-review` → 기록 → revise-claude-md → 요청 시 병합). 토큰 갱신 경합이 있어 리뷰 강도는 `medium`을 제안한다 (실행 전 승인).

## Global Constraints

- 패키지 매니저는 pnpm. 네이티브 의존성은 `pnpm expo install`, 순수 JS만 `pnpm add`. `npx` 금지.
- `ios/`, `android/`는 커밋하지 않는다 (CNG). 네이티브 설정은 `app.config.ts`와 config plugin으로만 바꾼다.
- 앱 이름 `벽헤는밤`, slug·scheme `theclimbingnight`, iOS bundleIdentifier·Android package `com.theclimbingnight.app`.
- 디렉터리: `app/` · `components/ui/` · `components/<feature>/` · `hooks/` · `lib/` · `types/api/`. `src/`는 쓰지 않는다. 경로 별칭은 `@/*` → `./*`.
- 파일명: 컴포넌트 `PascalCase.tsx`, 훅 `use-xxx.ts`, 그 외 `kebab-case.ts`. `any` 금지.
- 컴포넌트에서 `fetch` 직접 호출 금지. 토큰은 `expo-secure-store`에만 저장하고 로그·URL·라우트 파라미터에 넣지 않는다 (가입 토큰, 푸시 토큰 포함).
- `EXPO_PUBLIC_*`에 비밀값을 넣지 않는다. 네이버 Client Secret만 예외다: 네이버 SDK가 앱 안의 secret을 요구해 번들에서 추출될 수 있다. 서버 인증에는 쓰지 않는다.
- 서버 에러 `message`는 그대로 보여준다. 역할에 따라 화면을 숨기는 것은 보조 수단이다.
- 카피는 한국어, 이모지·em dash 없음.
- 애니메이션은 Reanimated로 `transform`·`opacity`만 움직이고, 시스템 "동작 줄이기"가 켜지면 최종 상태를 바로 보여준다.
- 터치 영역 최소 44pt(iOS)/48dp(Android), 본문 대비 4.5:1 이상, 라이트·다크 모두 지원, 아이콘 버튼에 `accessibilityLabel`.
- 테스트는 RNTL 14 기준: `render`·`fireEvent`·`renderHook`은 `await`한다. 실제 네트워크·SDK 호출 금지.

## 디자인 방향 (제안)

로고·브랜드 자료를 받으면 토큰 값만 바꾼다. 화면은 토큰을 통해서만 색을 쓴다.

- **개념:** 저녁 암장. 짙은 남색 벽에 박힌 홀드 하나가 유일한 강조색이다. 서명 요소는 로그인 화면의 "루트": 홀드가 아래에서 위로 하나씩 켜지고 마지막 홀드(톱)에 앱 이름이 놓인다.
- **색:** 벽 `#18243D`, 깊은 벽 `#101A2E`, 분필 `#EEF2F8`, 홀드 노랑 `#FFC83D`(위 글자는 항상 `#18243D`), 흐린 글자 다크 `#AAB7CF` / 라이트 `#4A5873`, 오류 다크 `#FF9B91` / 라이트 `#B3261E`.
- **글꼴:** Pretendard 한 종류 (Regular 400, SemiBold 600, ExtraBold 800). 크기 13 / 15 / 17 / 22 / 30.
- **모양:** 버튼은 알약, 입력은 12, 묶음 카드는 20. 왼쪽 정렬. 간격은 4의 배수.
- **모션:** 스프링 `{ damping: 18, stiffness: 180 }`, 누름 0.97배. 탭 표시는 다음 탭으로 손을 뻗듯 늘었다 줄며 이동한다.

## Review Focus

1. **여러 화면이 동시에 401을 받음** → 리프레시는 한 번만 보낸다. 두 번 보내면 서버가 두 번째를 거부해 사용자가 로그아웃된다. Task 3 `sends a single refresh for concurrent 401s`.
2. **오프라인이거나 서버가 502 HTML을 줌** → 로그아웃되지 않고 오류만 보인다. Task 3 `keeps the session when refresh fails on the network`, `keeps the session when the server returns HTML`.
3. **갱신 중에 로그아웃하거나 다른 계정으로 로그인** → 늦게 온 갱신 응답이 예전 세션을 되살리지 않는다. Task 3 `ignores a refresh response that arrives after logout`.
4. **소셜 로그인 창을 닫음** → 오류 문구 없이 로그인 화면에 그대로 있다. Task 4 `treats … cancellation`, Task 5 `shows no error when the user cancels`.
5. **한 기기에서 A 로그아웃 후 B 로그인, 또는 앱이 꺼진 상태에서 푸시를 누름** → A의 푸시 토큰이 지워지고, 푸시는 알맞은 화면을 한 번만 연다. Task 8 `sends the registered push token on logout`, `handles a notification response only once`.

## 파일 구조

| 파일 | 책임 |
|---|---|
| `app.config.ts` | 앱 식별자, config plugin. 키가 없는 소셜 plugin은 건너뜀 |
| `lib/config.ts` | `EXPO_PUBLIC_*` 읽기 |
| `lib/api-error.ts`, `lib/api.ts` | 오류 타입, `apiFetch` |
| `lib/session-store.ts` | 세션 보관·저장·구독 |
| `lib/auth-api.ts` | 로그인·가입·로그아웃 호출 |
| `lib/social/*` | 제공자별 SDK 어댑터 |
| `lib/push.ts`, `lib/push-route.ts` | 푸시 토큰 등록, 푸시 → 화면 결정 |
| `lib/query-client.ts` | React Query 설정 |
| `lib/theme.ts`, `hooks/use-theme.ts` | 디자인 토큰 |
| `hooks/use-session.ts`, `hooks/use-push.ts` | 화면용 훅 |
| `components/ui/*` | 버튼·입력·글자·화면 틀 |
| `components/auth/*`, `components/navigation/*` | 로그인 화면 조각, 탭 막대 |
| `app/_layout.tsx`, `app/(tabs)/*`, `app/login.tsx`, `app/signup.tsx` | 라우트 |

---

### Task 1: 프로젝트 골격과 테스트 환경

**Files:**
- Create: `apps/mobile/` 전체 골격, `app.config.ts`, `eas.json`, `pnpm-workspace.yaml`, `tsconfig.json`, `eslint.config.js`, `jest.config.js`, `jest.setup.ts`, `.prettierrc`, `.env.example`, `lib/config.ts`, `app/_layout.tsx`, `app/index.tsx`
- Modify: `CLAUDE.md`
- Test: `lib/__tests__/config.test.ts`, `__tests__/app-config.test.ts`

**Interfaces:**
- Produces: `config: { apiUrl: string; kakaoNativeAppKey?: string; naver?: { clientId: string; clientSecret: string }; google?: { webClientId: string; iosClientId: string } }`, `readConfig(env: Record<string, string | undefined>): typeof config`. 스크립트 `start`, `lint`, `typecheck`, `test`.

- [ ] **Step 1: 브랜치와 골격 생성**

```bash
git switch -c feat/mobile-foundation dev
cd apps && pnpm create expo-app mobile --template default@sdk-58 --no-install
```

템플릿의 `src/`, `scripts/reset-project.js`, `app.json`, 예제 이미지, 웹 전용 의존성(`react-dom`, `react-native-web`, `@expo/ui`)을 지운다. 중첩 `.git`이 생겼으면 지운다. `pnpm-workspace.yaml`에 `nodeLinker: hoisted`를 쓴다 (네이티브 라이브러리가 중첩 경로를 찾지 못하는 문제 방지).

- [ ] **Step 2: 의존성 설치**

```bash
pnpm install
pnpm expo install expo-dev-client expo-secure-store expo-notifications expo-device expo-constants expo-apple-authentication expo-build-properties expo-font expo-splash-screen expo-system-ui expo-haptics react-native-svg @react-native-kakao/core @react-native-kakao/user @react-native-seoul/naver-login @react-native-google-signin/google-signin
pnpm add @tanstack/react-query phosphor-react-native
pnpm expo install --dev jest-expo jest @react-native/jest-preset eslint eslint-config-expo
pnpm add -D @types/jest @testing-library/react-native@^14 test-renderer@^1 prettier
pnpm expo install --check
```

Expected: 마지막 명령이 버전 불일치 없이 끝난다. pnpm이 빌드 스크립트 승인을 요구하면 필요한 패키지만 `pnpm-workspace.yaml`에 허용한다.

- [ ] **Step 3: 설정 파일 작성**
  - `package.json` scripts: `start: expo start --dev-client`, `lint: expo lint`, `typecheck: tsc --noEmit`, `test: jest`, `ios: expo run:ios`, `android: expo run:android`.
  - `jest.config.js`: preset `jest-expo`, `setupFilesAfterEnv: ['<rootDir>/jest.setup.ts']`, `moduleNameMapper: { '^@/(.*)$': '<rootDir>/$1' }`. `jest.setup.ts`는 `require('react-native-reanimated').setUpTests()`를 부른다.
  - `tsconfig.json`: `extends: expo/tsconfig.base`, `strict: true`, `paths: { "@/*": ["./*"] }`.
  - `eas.json`: 프로필 `development`(`developmentClient: true`, `distribution: internal`), `development-simulator`(`development` 확장 + `ios.simulator: true`), `preview`(internal), `production`. 프로필마다 `environment`를 지정한다.
  - `.env.example`: `EXPO_PUBLIC_API_URL`, `EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY`, `EXPO_PUBLIC_NAVER_CLIENT_ID`, `EXPO_PUBLIC_NAVER_CLIENT_SECRET`, `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`, `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID`, `EAS_PROJECT_ID`, `GOOGLE_SERVICES_JSON`. 로컬 API 주소 예시(iOS 시뮬레이터 `http://localhost:8080`, Android 에뮬레이터 `http://10.0.2.2:8080`)를 주석으로 적는다.

- [ ] **Step 4: 실패하는 테스트 작성**

```ts
// lib/__tests__/config.test.ts
it('throws when the API URL is missing', () => {
  expect(() => readConfig({})).toThrow('EXPO_PUBLIC_API_URL');
});
it('strips a trailing slash from the API URL', () => {
  expect(readConfig({ EXPO_PUBLIC_API_URL: 'https://a.test/' }).apiUrl).toBe('https://a.test');
});
it('leaves naver undefined when only the client id is set', () => {
  expect(readConfig({ EXPO_PUBLIC_API_URL: 'https://a.test', EXPO_PUBLIC_NAVER_CLIENT_ID: 'x' }).naver).toBeUndefined();
});

// __tests__/app-config.test.ts : env를 바꿔 가며 app.config.ts의 기본 export를 호출
it('uses the fixed identifiers', () => {
  expect(load({}).ios?.bundleIdentifier).toBe('com.theclimbingnight.app');
  expect(load({}).android?.package).toBe('com.theclimbingnight.app');
});
it('omits social plugins whose keys are missing', () => {
  expect(pluginNames(load({}))).not.toContain('@react-native-kakao/core');
});
it('adds the kakao plugin when the key is set', () => {
  expect(pluginNames(load({ EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY: 'k' }))).toContain('@react-native-kakao/core');
});
it('derives the google url scheme from the iOS client id', () => {
  const c = load({ EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID: 'w', EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID: '123-abc.apps.googleusercontent.com' });
  expect(pluginOptions(c, '@react-native-google-signin/google-signin')).toEqual({ iosUrlScheme: 'com.googleusercontent.apps.123-abc' });
});
```

`load`, `pluginNames`, `pluginOptions`는 이 테스트 파일 안의 도우미다.

- [ ] **Step 5: 실패 확인** — `pnpm test`. Expected: `readConfig`와 `app.config`가 없어 FAIL.

- [ ] **Step 6: `lib/config.ts`와 `app.config.ts` 구현**
  - `lib/config.ts`는 `process.env.EXPO_PUBLIC_X`를 **점 표기로 하나씩** 읽어 `readConfig`에 넘긴다 (Expo는 정적 표기만 번들에 넣는다). 빈 문자열은 없는 값으로 본다.
  - plugin: `expo-router`, `expo-secure-store`, `expo-apple-authentication`, `['expo-notifications', { color: '#FFC83D', defaultChannel: 'default' }]`, `['expo-build-properties', { android: { extraMavenRepos: ['https://devrepo.kakao.com/nexus/content/groups/public/'] } }]`, splash·font.
  - 카카오: `{ nativeAppKey, android: { authCodeHandlerActivity: true }, ios: { handleKakaoOpenUrl: true } }`. 네이버: `{ urlScheme: 'theclimbingnightnaver' }`.
  - `ios.usesAppleSignIn: true`, `userInterfaceStyle: 'automatic'`, `extra.eas.projectId = EAS_PROJECT_ID`, `android.googleServicesFile`은 `GOOGLE_SERVICES_JSON`이 있을 때만.
  - `experiments: { typedRoutes: true, reactCompiler: true }`.

- [ ] **Step 7: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 모두 통과.

- [ ] **Step 8: 두 소셜 plugin이 함께 적용되는지 확인**

카카오와 네이버 plugin이 모두 iOS `AppDelegate`를 문자열 치환으로 고친다. 가짜 키로 prebuild해 확인한다.

```bash
EXPO_PUBLIC_API_URL=http://localhost:8080 EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY=k EXPO_PUBLIC_NAVER_CLIENT_ID=n EXPO_PUBLIC_NAVER_CLIENT_SECRET=s EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID=w EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID=1-a.apps.googleusercontent.com pnpm expo prebuild --clean --no-install
grep -rnE 'RNCKakaoUserUtil|NaverThirdPartyLoginConnection' ios/*/AppDelegate.swift
grep -n 'AuthCodeHandlerActivity' android/app/src/main/AndroidManifest.xml
```

Expected: `AppDelegate.swift`에 카카오와 네이버 처리 코드가 모두 있고, 매니페스트에 카카오 액티비티가 있다. 하나라도 빠지면 plugin 순서를 바꿔 본다. 그래도 빠지면 `plugins/with-social-open-url.ts`에 빠진 처리만 넣는 config plugin을 만든다. 확인 뒤 `ios/`, `android/`를 지운다.

- [ ] **Step 9: `CLAUDE.md` 갱신 후 커밋** — "앱 디렉터리는 apps/api만 있다"를 "apps/api, apps/mobile이 있다"로, 명령 표에 `pnpm typecheck`를 추가하고 `pnpm start` 설명을 development build 기준으로 고친다. `Feat(mobile) : Expo 프로젝트 골격 추가`

---

### Task 2: 세션 저장소

**Files:**
- Create: `types/api/user.ts`, `types/api/auth.ts`, `lib/session-store.ts`, `hooks/use-session.ts`
- Test: `lib/__tests__/session-store.test.ts`

**Interfaces:**
- Produces:

```ts
type Role = 'USER' | 'MEMBER' | 'ADMIN';
interface UserResponse { id: number; nickname: string; role: Role }
interface TokenResponse { accessToken: string; accessTokenExpiresAt: string; refreshToken: string; user: UserResponse }
type LoginResponse = { status: 'LOGGED_IN'; tokens: TokenResponse } | { status: 'SIGNUP_REQUIRED'; signupToken: string };
type SocialProvider = 'KAKAO' | 'NAVER' | 'GOOGLE' | 'APPLE';

type SessionState = { status: 'loading' } | { status: 'guest' } | { status: 'authenticated'; tokens: TokenResponse };
const sessionStore: {
  getState(): SessionState;
  getGeneration(): number;                 // signIn·signOut마다 +1, updateTokens·updateUser에서는 그대로
  subscribe(listener: () => void): () => void;
  load(): Promise<void>;
  signIn(tokens: TokenResponse): Promise<void>;
  updateTokens(tokens: TokenResponse, generation: number): Promise<boolean>; // 세대가 다르면 버리고 false
  updateUser(user: UserResponse): Promise<void>;
  signOut(): Promise<void>;
};
function useSession(): SessionState;       // useSyncExternalStore
```

- [ ] **Step 1: 실패하는 테스트 작성** (`expo-secure-store`는 메모리 Map으로 모킹, 키 `session`)

```ts
it('becomes guest when nothing is stored');                    // load() 뒤 status 'guest'
it('restores a stored session');                               // 저장된 tokens와 toEqual
it('becomes guest and deletes the value when it is not valid JSON'); // deleteItemAsync('session') 호출
it('becomes guest when a required field is missing');          // refreshToken 없는 JSON
it('becomes guest when secure store throws');                  // getItemAsync reject
it('discards updateTokens from an old generation');            // signOut 뒤 updateTokens(t, 옛 세대) → false, status 'guest', 저장소 비어 있음
it('persists writes in call order');                           // signIn(a), signOut() 연속 호출, setItemAsync를 늦게 끝내도 최종 저장소 비어 있음
it('notifies subscribers on change');                          // listener 1회
```

각 테스트의 단언은 주석의 값 그대로 `expect`로 쓴다.

- [ ] **Step 2: 실패 확인** — `pnpm test session-store`. Expected: FAIL (모듈 없음).
- [ ] **Step 3: 구현** — 메모리 상태를 먼저 바꾸고 저장소 쓰기는 하나의 Promise 체인으로 직렬화한다. 저장 옵션은 `keychainAccessible: AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`. 쓰기 실패는 삼키고 메모리 상태를 유지한다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 통과.
- [ ] **Step 5: 커밋** `Feat(mobile) : 세션 저장소 추가`

---

### Task 3: API 클라이언트와 토큰 갱신

**Files:**
- Create: `lib/api-error.ts`, `lib/api.ts`, `lib/auth-api.ts`, `lib/query-client.ts`
- Test: `lib/__tests__/api.test.ts`, `lib/__tests__/auth-api.test.ts`

**Interfaces:**
- Consumes: Task 1 `config`, Task 2 `sessionStore`와 타입
- Produces:

```ts
class ApiError extends Error { constructor(code: string, message: string, status: number, fields?: { field: string; reason: string }[]) }
// 클라이언트 전용 코드: 'CLIENT_NETWORK'(status 0, "네트워크 연결을 확인한 뒤 다시 시도해 주세요."),
//                    'CLIENT_BAD_RESPONSE'("서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해 주세요.")
function apiFetch<T>(path: string, options?: { method?: 'GET'|'POST'|'PUT'|'PATCH'|'DELETE'; body?: unknown; auth?: boolean }): Promise<T>; // auth 기본 true, 204는 undefined
function setSessionExpiredHandler(handler: () => void): void;
const authApi: {
  login(provider: SocialProvider, token: string): Promise<LoginResponse>;
  signup(signupToken: string, nickname: string): Promise<TokenResponse>;
  logout(refreshToken: string, expoPushToken?: string): Promise<void>;
  getMe(): Promise<UserResponse>;
};
const queryClient: QueryClient;
```

- [ ] **Step 1: 실패하는 테스트 작성** (`global.fetch` 모킹, 시간은 jest fake timer)

```ts
it('sends the bearer token and unwraps data');          // Authorization 'Bearer A1', 결과 toEqual(data)
it('omits the bearer token when auth is false');
it('throws ApiError with code, message and fields');    // COMMON_VALIDATION_FAILED, fields 1개
it('returns undefined for 204');
it('refreshes once and retries on AUTH_REQUIRED');      // 재시도는 'Bearer A2', 세션 tokens는 새 값
it('sends a single refresh for concurrent 401s');       // 동시 호출 3개, /auth/refresh 호출 1회, 모두 성공
it('retries with the stored token when another call already refreshed'); // A1로 보낸 요청의 401이 세션이 A2가 된 뒤 도착 → 리프레시 0회, A2로 재시도
it('does not refresh on AUTH_INVALID_SOCIAL_TOKEN');    // /auth/refresh 0회
it('does not refresh when there is no session');        // 세션 만료 처리기도 0회
it('signs out and calls the expired handler once when refresh returns AUTH_REQUIRED'); // 동시 401 2개여도 1회
it('keeps the session when refresh fails on the network');      // CLIENT_NETWORK, status 'authenticated'
it('keeps the session when the server returns HTML');           // 502 text/html, CLIENT_BAD_RESPONSE
it('ignores a refresh response that arrives after logout');     // status 'guest' 유지, 재시도 없음
it('does not retry more than once when the retried call is also 401');
it('times out after 15 seconds with CLIENT_NETWORK');
// auth-api.test.ts
it('calls login, signup and logout without a bearer token');
it('sends expoPushToken on logout only when given');
```

- [ ] **Step 2: 실패 확인** — `pnpm test api`. Expected: FAIL.
- [ ] **Step 3: 구현**
  - 주소는 `${config.apiUrl}/api/v1${path}`. `AbortController`로 15초 제한.
  - 갱신 조건: 상태 401, 코드 `AUTH_REQUIRED`, `auth: true`, 요청에 토큰을 실어 보냈을 때. 요청을 보낼 때의 세대를 기억해 재시도 전에 같은 세대인지 확인하고, 다르면 원래 `ApiError`를 던진다.
  - 진행 중인 갱신 Promise 하나를 공유하고 끝나면 비운다. 갱신은 `POST /auth/refresh { refreshToken }`이며 `apiFetch`를 다시 타지 않는다.
  - `queryClient`: `ApiError`의 status가 400~499면 재시도하지 않고 그 밖은 2회, `staleTime` 30초.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 통과.
- [ ] **Step 5: 커밋** `Feat(mobile) : API 클라이언트와 토큰 갱신 추가`

---

### Task 4: 소셜 로그인 어댑터

**Files:**
- Create: `lib/social/types.ts`, `lib/social/kakao.ts`, `lib/social/naver.ts`, `lib/social/google.ts`, `lib/social/apple.ts`, `lib/social/index.ts`
- Test: `lib/social/__tests__/*.test.ts`

**Interfaces:**
- Consumes: Task 1 `config`, Task 2 `SocialProvider`
- Produces:

```ts
type SocialResult = { type: 'success'; token: string } | { type: 'cancelled' };
interface SocialAdapter { provider: SocialProvider; isAvailable(): boolean; signIn(): Promise<SocialResult>; signOut(): Promise<void> }
class SocialSignInError extends Error {}   // message: "로그인하지 못했습니다. 잠시 후 다시 시도해 주세요."
function availableAdapters(): SocialAdapter[]; // 순서 KAKAO, NAVER, GOOGLE, APPLE
function signOutSocial(): Promise<void>;       // 사용 가능한 어댑터 전부, 실패는 무시
```

- [ ] **Step 1: 실패하는 테스트 작성** (SDK는 `jest.mock`)

```ts
// kakao
it('returns the access token');                            // { type:'success', token:'kakao-access' }
it.each(['Cancelled', 'AccessDenied'])('treats %s as cancellation'); // reject({ code }) → { type:'cancelled' }
it('initializes the SDK once');                            // signIn 2회, initializeKakaoSDK 1회
// naver
it('returns the access token from successResponse');
it('treats isCancel as cancellation');                     // { isSuccess:false, failureResponse:{ message:'', isCancel:true } }
// google
it('returns the id token');                                // { type:'success', data:{ idToken:'id' } }
it('treats a cancelled response as cancellation');         // { type:'cancelled', data:null }
it('throws when the id token is null');                    // SocialSignInError
// apple
it('returns the identity token');
it('treats ERR_REQUEST_CANCELED as cancellation');
it('is unavailable on Android');
// 공통
it.each(providers)('wraps unknown %s failures in SocialSignInError');
it('lists only configured providers in order');            // 카카오 키만 + iOS → ['KAKAO','APPLE']
it('continues signing out when one provider fails');
```

- [ ] **Step 2: 실패 확인** — `pnpm test social`. Expected: FAIL.
- [ ] **Step 3: 구현**
  - 카카오: `initializeKakaoSDK(nativeAppKey)` 후 `login()`의 `accessToken`.
  - 네이버: `initialize({ appName: '벽헤는밤', consumerKey, consumerSecret, serviceUrlSchemeIOS: 'theclimbingnightnaver' })` 후 `login()`의 `successResponse.accessToken`.
  - 구글: `configure({ webClientId, iosClientId })`, Android만 `hasPlayServices({ showPlayServicesUpdateDialog: true })`, `signIn()`의 `data.idToken`. `signOut`을 먼저 불러 계정 선택 창이 항상 뜨게 한다. Android에서 `iosClientId`를 넘겨도 무시된다.
  - Apple: `signInAsync()`의 `identityToken`(scope 요청 없음). `isAvailable`은 `Platform.OS === 'ios'`. `signOut`은 아무것도 하지 않는다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 통과.
- [ ] **Step 5: 커밋** `Feat(mobile) : 소셜 로그인 어댑터 추가`

---

### Task 5: 디자인 토큰과 기본 컴포넌트

**Files:**
- Create: `lib/theme.ts`, `hooks/use-theme.ts`, `components/ui/AppText.tsx`, `Button.tsx`, `TextField.tsx`, `Screen.tsx`, `Notice.tsx`, `FadeInItem.tsx`, `assets/fonts/Pretendard-{Regular,SemiBold,ExtraBold}.otf`, `assets/fonts/LICENSE.txt`
- Test: `lib/__tests__/theme.test.ts`, `components/ui/__tests__/Button.test.tsx`, `TextField.test.tsx`, `FadeInItem.test.tsx`

**Interfaces:**
- Produces:

```ts
interface Theme { colors: { background; surface; text; textMuted; accent; onAccent; border; danger: string }; space(n: number): number; radius: { pill: 999; input: 12; card: 20 }; motion: { spring: { damping: 18; stiffness: 180 }; pressScale: 0.97 } }
const lightTheme: Theme; const darkTheme: Theme; function useTheme(): Theme;
function contrastRatio(a: string, b: string): number;
<AppText variant="caption|body|label|title|display" tone="default|muted|danger|onAccent" />   // 13/15/17/22/30
<Button label variant="primary|secondary|text" loading? disabled? icon? onPress />
<TextField label value onChangeText error? helper? maxLength? />
<Screen scroll? />  <Notice title description actionLabel? onAction? />  <FadeInItem index />
```

- [ ] **Step 1: 실패하는 테스트 작성**

```ts
it.each([lightTheme, darkTheme])('meets 4.5:1 for text, muted text, danger and accent label');
   // (text|textMuted|danger) 대 background·surface, onAccent 대 accent 모두 >= 4.5
it('computes 21 for black on white');                       // contrastRatio('#000000','#FFFFFF') → 21
it('does not call onPress while loading or disabled');      // Button
it('exposes busy and disabled state to accessibility');     // accessibilityState { busy:true, disabled:true }
it('ignores a second press while loading');
it('announces the error and links it to the field');        // TextField error가 role alert로 보임
it('renders the final state when reduce motion is on');     // FadeInItem opacity 1, translateY 0
```

- [ ] **Step 2: 실패 확인** — `pnpm test theme components/ui`. Expected: FAIL.
- [ ] **Step 3: 구현** — "디자인 방향"의 값을 쓴다. 다크 background `#101A2E`·surface `#18243D`·text `#EEF2F8`, 라이트 background `#EEF2F8`·surface `#FFFFFF`·text `#18243D`, accent는 둘 다 `#FFC83D`. 라이트에서 노랑을 글자색으로 쓰지 않는다. 글꼴은 `expo-font` plugin으로 넣고, 파일은 `pretendard@1.3.9` 배포본 `dist/public/static/`에서 복사한다. 패키지 의존성으로는 넣지 않는다. 버튼 누름은 스프링 축소 + `expo-haptics` 가벼운 진동.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 통과.
- [ ] **Step 5: 커밋** `Feat(mobile) : 디자인 토큰과 기본 컴포넌트 추가`

---

### Task 6: 앱 시작과 탭 구조

**Files:**
- Create: `app/(tabs)/_layout.tsx`, `app/(tabs)/index.tsx`, `meetups.tsx`, `goods.tsx`, `my.tsx`, `components/navigation/AnimatedTabBar.tsx`, `lib/sign-out.ts`
- Modify: `app/_layout.tsx`
- Delete: `app/index.tsx`
- Test: `__tests__/app-start.test.tsx`, `components/navigation/__tests__/AnimatedTabBar.test.tsx`, `lib/__tests__/sign-out.test.ts`

**Interfaces:**
- Consumes: Task 2 `sessionStore`·`useSession`, Task 3 `setSessionExpiredHandler`·`queryClient`·`authApi`, Task 4 `signOutSocial`, Task 5 컴포넌트
- Produces: 라우트 `/`, `/meetups`, `/goods`, `/my`. `signOut(options?: { expoPushToken?: string }): Promise<void>`

- [ ] **Step 1: 실패하는 테스트 작성** (`renderRouter`는 `expo-router/testing-library`)

```tsx
it('keeps the splash until the session is loaded');          // load가 끝나기 전 SplashScreen.hideAsync 0회, 후 1회
it('shows the four tabs to a guest');                        // 홈, 벙, 굿즈, 마이
it('asks a guest to log in on the meetups tab');             // "로그인하기" 버튼 → /login
it('asks a USER for membership verification on the meetups tab'); // "동호회원 인증이 필요해요"
it('shows the meetups placeholder to a MEMBER');             // "벙 일정을 준비하고 있어요"
it('shows the nickname and logout on the my tab');
it('refreshes the cached user after start');                 // getMe 결과 role MEMBER → 벙 탭이 MEMBER 화면
it('stays usable when getMe fails on the network');          // 저장된 닉네임 표시
it('opens login and clears cached queries when the session expires'); // 경로 '/login', queryClient 비어 있음
it('marks the focused tab as selected');                     // AnimatedTabBar accessibilityState.selected
// sign-out.test.ts
it('clears the local session even when the server call fails');
it('clears the local session before waiting for the server');
it('clears the query cache so the next account sees no old data');
it('skips the server call when already a guest');
```

- [ ] **Step 2: 실패 확인** — `pnpm test app-start AnimatedTabBar sign-out`. Expected: FAIL.
- [ ] **Step 3: 구현**
  - 루트: `SplashScreen.preventAutoHideAsync()` → `sessionStore.load()`와 글꼴 → 숨김. `QueryClientProvider`, Router `ThemeProvider`, `Stack`.
  - 로그인 상태로 시작하거나 앱이 다시 활성화되면 `getMe`로 `updateUser`한다 (다른 기기에서 승인된 역할 반영). React Query 키는 `['users', 'me']`.
  - 탭은 `expo-router/js-tabs`의 `Tabs`에 `tabBar={AnimatedTabBar}`. 아이콘은 `phosphor-react-native`(House, CalendarDots, Tote, User), 선택은 fill, 나머지는 regular. 표시 막대는 `transform`만으로 늘어나며 이동한다.
  - `signOut`: refreshToken을 꺼낸 뒤 로컬 세션·쿼리 캐시를 먼저 비우고, 서버 로그아웃과 `signOutSocial`은 실패를 무시한다.
  - 굿즈 탭은 `Notice`("굿즈를 준비하고 있어요")만 둔다. 홈·벙·마이의 실제 내용은 계획 6, 굿즈 웹뷰는 계획 7.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 통과.
- [ ] **Step 5: 커밋** `Feat(mobile) : 앱 시작 흐름과 탭 구조 추가`

---

### Task 7: 로그인과 닉네임 가입 화면

**Files:**
- Create: `app/login.tsx`, `app/signup.tsx`, `components/auth/RouteHolds.tsx`, `components/auth/SocialButtons.tsx`, `hooks/use-login.ts`, `hooks/use-signup.ts`, `lib/pending-signup.ts`, `lib/nickname.ts`
- Test: `__tests__/login.test.tsx`, `__tests__/signup.test.tsx`, `lib/__tests__/nickname.test.ts`

**Interfaces:**
- Consumes: Task 3 `authApi`, Task 4 `availableAdapters`, Task 6 라우트
- Produces: 라우트 `/login`, `/signup`. `pendingSignup: { set(token: string): void; get(): string | null; clear(): void }` (메모리에만). `nicknameError(value: string): string | null`

- [ ] **Step 1: 실패하는 테스트 작성**

```tsx
// nickname
it.each(['가나', 'abc_1', '가'.repeat(16)])('accepts %s');            // null
it.each(['', '가', '가'.repeat(17), 'a b', 'a-b'])('rejects %s');     // "닉네임은 2~16자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다."
// login
it('lists kakao, naver, google and then apple on iOS');
it('hides apple on Android');
it('signs in and returns to the tabs');                    // LOGGED_IN → status 'authenticated', 경로 '/'
it('moves to signup when signup is required');             // 경로 '/signup', URL에 토큰 없음
it('shows no error when the user cancels');                // alert 없음, 버튼 다시 활성
it('shows the server message on failure');                 // AUTH_PROVIDER_UNAVAILABLE의 message
it('disables every button while one provider is signing in');
it('stays on login when AUTH_INVALID_SOCIAL_TOKEN is returned'); // 리프레시 0회
// signup
it('returns to login when there is no pending signup token');
it('creates the account and returns to the tabs');         // signup('st','가나'), 경로 '/'
it('shows the taken-nickname message under the field');    // USER_NICKNAME_TAKEN
it('returns to login when the signup token expired');      // AUTH_SIGNUP_EXPIRED
it('returns to login when already registered');            // AUTH_ALREADY_REGISTERED
it('sends one request when submit is pressed twice');
```

- [ ] **Step 2: 실패 확인** — `pnpm test login signup nickname`. Expected: FAIL.
- [ ] **Step 3: 구현**
  - 닉네임 규칙은 `NicknamePolicy.java`와 같다: 앞뒤 공백을 없앤 뒤 `^[가-힣a-zA-Z0-9_]{2,16}$`. 최종 판정은 서버다.
  - 로그인 화면: `RouteHolds`(홀드가 아래에서 위로 켜지고 톱에 "벽헤는밤") 아래에 로그인 버튼. 설명은 "벙 신청부터 참석 기록까지, 동호회 활동을 한곳에서." 오른쪽 위에 "둘러보기"로 탭에 돌아간다.
  - 각 제공자 버튼은 브랜드 지침의 색과 공식 심볼을 쓴다 (카카오 `#FEE500`, 네이버 `#03C75A`, 구글 지침 버튼). Apple은 `AppleAuthenticationButton`을 쓴다. 심볼 파일을 직접 그리지 않고 각 사 공식 자료를 받는다. 네이버의 흰 글자처럼 지침이 정한 색은 대비 4.5:1 규칙의 예외다.
  - 사용 가능한 어댑터가 없으면 `Notice` "로그인 설정이 아직 없어요"를 보인다.
  - 만료·중복 가입으로 로그인 화면에 돌아올 때 서버 문구를 함께 보여준다. 로그인·가입 완료와 실패 흔들림에 애니메이션과 진동을 넣는다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 통과.
- [ ] **Step 5: 커밋** `Feat(mobile) : 로그인과 닉네임 가입 화면 추가`

---

### Task 8: 푸시 등록과 푸시 이동

**Files:**
- Create: `lib/push.ts`, `lib/push-route.ts`, `hooks/use-push.ts`
- Modify: `app/_layout.tsx`, `lib/sign-out.ts`
- Test: `lib/__tests__/push.test.ts`, `lib/__tests__/push-route.test.ts`, `hooks/__tests__/use-push.test.tsx`, `lib/__tests__/sign-out.test.ts`

**Interfaces:**
- Consumes: Task 2 `sessionStore`, Task 3 `apiFetch`, Task 6 `signOut`
- Produces:

```ts
function registerPushToken(): Promise<void>;             // PUT /push-tokens { expoPushToken, platform: 'IOS'|'ANDROID' }
function getRegisteredPushToken(): Promise<string | null>;
function clearRegisteredPushToken(): Promise<void>;
function routeForPush(data: unknown): Href | null;
function usePush(): void;
```

- [ ] **Step 1: 실패하는 테스트 작성**

```ts
// push-route
it.each([
  ['MEETUP_PROMOTED', '/meetups'], ['MEETUP_CANCELED', '/meetups'], ['MEETUP_CREATED', '/meetups'], ['MEETUP_REMINDER', '/meetups'],
  ['MEMBERSHIP_APPROVED', '/my'], ['MEMBERSHIP_REJECTED', '/my'], ['INQUIRY_ANSWERED', '/my'], ['NOTICE_PUBLISHED', '/'],
])('routes %s to %s');
it.each([null, undefined, 'x', {}, { type: 1 }, { type: 'UNKNOWN' }])('returns null for %p');
// push
it('creates the default Android channel before asking permission'); // setNotificationChannelAsync('default', …) 먼저
it('does nothing when permission is denied');                       // PUT 0회
it('does not ask again when permission cannot be asked');           // canAskAgain false → requestPermissionsAsync 0회
it('does nothing on a simulator');                                  // Device.isDevice false
it('does nothing for a guest');
it('registers the token with the platform');                        // PUT body
it('stores the registered token');                                  // getRegisteredPushToken → 같은 값
it('swallows registration failures');                               // reject하지 않음
it('does not register when the user logged out while fetching the token'); // 세대가 다르면 PUT 0회
// use-push
it('registers on start when logged in and again after login');
it('opens the routed screen when a notification is tapped');
it('handles a notification response only once');                    // 재렌더해도 이동 1회, clearLastNotificationResponse 1회
it('waits for the session to load before routing a cold start tap');
it('refreshes the user when a membership push arrives');            // MEMBERSHIP_* 수신 → ['users','me'] invalidate
// sign-out
it('sends the registered push token on logout');                    // logout(refreshToken, token)
it('forgets the registered token on logout');
```

- [ ] **Step 2: 실패 확인** — `pnpm test push sign-out`. Expected: FAIL.
- [ ] **Step 3: 구현**
  - `setNotificationHandler`는 `shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false` (`shouldShowAlert`는 폐기됨).
  - 토큰은 `getExpoPushTokenAsync({ projectId: Constants.expoConfig?.extra?.eas?.projectId })`. projectId가 없으면 등록하지 않는다.
  - 등록한 토큰은 SecureStore 키 `expoPushToken`에 둔다. `signOut`의 인자 `expoPushToken`은 없애고 저장된 값을 읽어 보낸다. 세션을 비운 뒤 서버에 보낸다.
  - 탭 처리는 `useLastNotificationResponse`와 `clearLastNotificationResponse`.
  - 토큰 원문은 로그에 남기지 않는다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`. Expected: 통과.
- [ ] **Step 5: 커밋** `Feat(mobile) : 푸시 등록과 푸시 화면 이동 추가`

---

### Task 9: 문서 반영과 실기기 확인

**Files:**
- Create: `apps/mobile/README.md`
- Modify: `docs/architecture.md`, `.claude/rules/mobile.md`

- [ ] **Step 1: 문서 반영**
  - `apps/mobile/README.md`: 준비물 표, development build 만드는 법, 로컬 API 연결, Android 키 해시·SHA-1 얻는 법.
  - `docs/architecture.md`: 13장에 앱 식별자와 SDK 58, 6장에 네이버 Client Secret이 앱 번들에 들어가는 점, 14장에서 정해진 것(React Query, phosphor-react-native, 디자인 토큰 위치).
  - `.claude/rules/mobile.md`: 세션은 `lib/session-store.ts`, 소셜 SDK는 `lib/social/`에서만 import, 색은 `useTheme`로만.

- [ ] **Step 2: 자동 검사** — `pnpm lint && pnpm typecheck && pnpm test && pnpm expo install --check`. Expected: 모두 통과.

- [ ] **Step 3: 로컬 빌드** — `pnpm expo run:ios`, `pnpm expo run:android`. Expected: 두 플랫폼 모두 홈 탭이 열린다. 키가 없어도 빌드는 되어야 한다. 화면을 라이트·다크, 동작 줄이기, 큰 글꼴에서 스크린샷으로 확인한다.

- [ ] **Step 4: 실제 로그인 확인** (사용자 준비물이 있을 때. 없으면 하지 못한 항목을 그대로 보고한다)

| 확인 | iOS | Android |
|---|---|---|
| 카카오 (카카오톡 앱 있음 / 없음) | | |
| 네이버 (앱 있음 / 없음) | | |
| 구글 | | |
| Apple | | 버튼 없음 |
| 각 로그인 창을 닫으면 오류 없이 돌아옴 | | |
| 신규 가입 → 닉네임 → 홈 | | |
| 앱 종료 후 다시 열면 로그인 유지 | | |
| 비행기 모드로 열어도 로그인 유지 | | |
| 로그아웃 후 다른 계정 로그인 | | |
| 푸시 수신, 앱이 꺼진 상태에서 눌러 이동 (실기기) | | |

- [ ] **Step 5: 커밋** `Docs : 모바일 앱 기반 문서 반영`

---

## 이 계획이 끝나면 되는 것

- iOS·Android development build에서 로그인·가입·로그아웃·푸시 등록이 되고 탭 네 개가 열린다.
- 계획 6이 이어받는 것: 탭 네 개의 자리 화면, `routeForPush`를 벙 상세·문의 상세로 세분화, 알림 설정, 닉네임 변경, 탈퇴(`DELETE /users/me` 뒤 로컬 `signOut`), 운영진 메뉴, FlashList 도입.
- 계획 7이 이어받는 것: 굿즈 탭 웹뷰, 공지 푸시의 웹뷰 이동, `react-native-webview`, `types/bridge.ts`.
- 계획 8이 이어받는 것: EAS 운영 환경변수, 스토어 아이콘·스플래시, `KAKAO_APP_ID`·`GOOGLE_CLIENT_IDS`·`APPLE_AUDIENCES` 운영 값, 카카오·구글에 스토어 서명 키 등록.
- 정해 둔 것(스펙에 없던 결정):
  - 앱 식별자 `com.theclimbingnight.app`, 디자인 방향 (둘 다 제안).
  - 비로그인도 탭을 볼 수 있다. 로그인 화면은 필요할 때 연다.
  - 네트워크·서버 장애로는 로그아웃하지 않는다. 서버가 `AUTH_REQUIRED`로 답할 때만 로그아웃한다.
  - 오프라인 로그아웃은 서버에 전달되지 않는다. 그 기기의 푸시 토큰은 다음 로그인이 소유자를 옮길 때까지 서버에 남는다.
  - 갱신 응답이 유실되면 다시 로그인해야 한다 (서버가 이전 토큰을 이미 폐기함).
  - 키가 없는 제공자의 버튼은 숨긴다.
  - 푸시 권한은 로그인 직후 묻는다.
