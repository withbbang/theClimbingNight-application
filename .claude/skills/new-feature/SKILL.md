---
name: new-feature
description: API(apps/api)부터 앱(apps/mobile)·웹(apps/web)까지 기능 하나를 끝까지 구현하는 절차. 새 엔드포인트·화면·도메인 기능을 추가할 때 사용.
---
# /new-feature <기능 설명>

API → 앱/웹 순서로 간다. 각 단계는 테스트가 통과해야 다음으로 넘어간다.

## 1. 범위 확정
- 영향 받는 `apps/api` 도메인과 화면을 각각 한 줄로 적는다. 화면은 `docs/architecture.md` 화면 표에서 네이티브(`apps/mobile`)인지 웹뷰(`apps/web`)인지 확인한다.
- 권한 대상(전체 / 로그인 사용자 / 동호회원 / 운영진)을 적는다.
- 새 API가 필요하면 `api-conventions` 스킬 포맷으로 URL·요청·응답 초안을 먼저 보여주고 **사용자 확인 후** 진행.

## 2. API (apps/api)
1. DTO(`XxxRequest` / `XxxResponse` record) → Service → Controller 순으로 작성.
2. 레이어 규칙은 `.claude/rules/api.md`를 따른다.
3. Service 단위 테스트(Mockito) + Controller `@WebMvcTest` 작성. 권한이 있으면 거부되는 경우도 테스트한다.
4. `./gradlew test` 통과 확인. 실패하면 여기서 고친다.

## 3-a. 네이티브 화면 (apps/mobile)
1. `types/api/`에 응답 타입 추가 (백엔드 DTO와 이름 일치).
2. `lib/api.ts`에 호출 함수 → `hooks/`에 React Query 훅 → 컴포넌트 → 라우트(`app/`) 순.
3. 컴포넌트 테스트(jest-expo + RNTL) 작성.
4. `pnpm lint && pnpm test` 통과 확인.

## 3-b. 웹뷰·관리자 화면 (apps/web)
1. 3-a와 같은 순서로 `apps/web`에 작성. 규칙은 `.claude/rules/web.md`.
2. 앱에서 여는 화면이면 앱 쪽 웹뷰 진입점과 브리지 메시지(`types/bridge.ts`)도 함께 고친다.
3. `pnpm lint && pnpm test` 통과 확인.

## 4. 마무리
- 바뀐 파일 목록과 **수동 확인 방법**(어느 화면에서 뭘 눌러보면 되는지, iOS·Android 둘 다)을 요약.
- 커밋은 요청이 있을 때만. 있으면 `.gitmessage.txt` 정책대로 API / 앱 / 웹 커밋 분리.
