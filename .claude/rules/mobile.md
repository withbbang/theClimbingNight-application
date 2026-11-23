---
paths:
  - "apps/mobile/**"
---
# apps/mobile — Expo / React Native / TypeScript

- 라우팅은 **Expo Router** (`app/` 파일 기반). 탭·스택 구성은 `docs/architecture.md`를 따른다.
- 네이티브 화면과 웹뷰를 나누는 기준은 `docs/architecture.md`의 화면 표가 정본이다. 표에 없는 화면을 새로 만들면 표부터 고친다.
- 네이티브 의존성은 `pnpm expo install <패키지>`로 설치한다 (SDK에 맞는 버전이 잡힘). `pnpm add`는 순수 JS 패키지에만.
- 네이티브 폴더(`ios/`, `android/`)는 커밋하지 않는다 (CNG). 네이티브 설정은 `app.config.ts`와 config plugin으로만 바꾼다.
- 데이터는 React Query로 조회. API 호출은 `lib/api.ts`의 래퍼를 통해서만. 컴포넌트에서 `fetch` 직접 호출 금지.
- API 주소는 `EXPO_PUBLIC_API_URL`. `EXPO_PUBLIC_*`는 앱 번들에 그대로 들어가니 비밀값을 넣지 않는다.
- 인증 토큰은 `expo-secure-store`에만 저장한다. AsyncStorage에 토큰 저장 금지.
- 미디어 업로드: API에서 presigned URL을 받아 R2로 직접 PUT. 업로드 전에 앱에서 리사이즈·압축한다. API 서버로 파일 본문을 보내지 않는다.
- 이미지 표시는 `expo-image` (캐시·placeholder). 피드 같은 긴 목록은 `FlatList`가 아닌 가상화 리스트(FlashList 등, 아키텍처 문서에서 확정)를 쓴다.
- 웹뷰 화면은 `react-native-webview`로 `apps/web` 페이지를 연다. 웹뷰와 앱 사이 통신은 `postMessage` 메시지 타입을 `types/bridge.ts` 한 곳에 정의한다.
- 디렉토리: `app/`(라우트) · `components/ui/`(범용) · `components/<feature>/`(도메인) · `hooks/` · `lib/` · `types/api/`.
- 파일명: 컴포넌트 `PascalCase.tsx`, 훅 `use-xxx.ts`, 그 외 `kebab-case.ts`. 라우트 파일은 Expo Router 규칙을 따른다.
- 타입: `any` 금지. API 응답 타입은 `types/api/`에 두고 백엔드 DTO 이름(`XxxResponse`)과 일치시킨다.
- import 순서: react → react-native → expo → 외부 패키지 → `@/` 절대경로 → 상대경로.
- 플랫폼 분기는 `Platform.OS` 또는 `.ios.tsx`/`.android.tsx` 파일로. 한쪽에서만 확인하고 끝내지 않는다.
