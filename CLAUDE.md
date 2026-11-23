# theClimbingNight

벽헤는밤 클라이밍 동호회 앱. 피드(사진·영상), 댓글·DM, 벙 참여·참석률, 굿즈 판매.
Expo(앱) + Next.js(웹뷰·관리자) + Spring Boot(API) 모노레포, 1인 개발.
원격 저장소: `https://github.com/withbbang/theClimbingNight-application.git`

## 구조

| 경로 | 역할 | 스택 | 도구 |
|---|---|---|---|
| `apps/mobile` | iOS·Android 앱 | Expo / React Native / TypeScript | pnpm · EAS |
| `apps/web` | 웹뷰 화면(공지·소개·약관·굿즈) + 관리자 | Next.js / React / TypeScript / Tailwind | pnpm |
| `apps/api` | REST API · WebSocket(DM) | Spring Boot / Java / JPA / MySQL | Gradle |
| `deploy/` | NAS(Synology DS1520+) Docker Compose · nginx | — | — |
| `docs/` | 아키텍처 등 설계 문서 | — | — |
| `.claude/` | Claude Code 설정 (rules · skills · agents · hooks) | — | — |

아직 앱 디렉터리는 없다. 만들기 전에 `docs/architecture.md`를 먼저 확정한다.

## 명령

```bash
# apps/mobile
pnpm start          # Expo 개발 서버
pnpm lint
pnpm test           # jest-expo
pnpm expo install <패키지>   # 네이티브 의존성은 SDK 호환 버전으로 설치

# apps/web
pnpm dev
pnpm lint
pnpm test           # Vitest (vitest run)
pnpm build

# apps/api
./gradlew bootRun
./gradlew test
./gradlew build
```

앱을 만들면 실제 스크립트 이름에 맞춰 이 표를 고친다.

## 항상 지킬 것

- 응답은 한국어로. `/code-review`·`/claude-md-management:*` 등 스킬·서브에이전트 결과도 한국어로 작성하고, 영어로 받은 결과는 한국어로 옮겨 보고한다.
- 패키지 매니저는 **pnpm**. npm / yarn / npx 명령 쓰지 않는다 (`npx expo ...` 대신 `pnpm expo ...`).
- 브랜치: `master`(배포용) · `dev` · 피처 브랜치. 피처 브랜치는 항상 `dev`에서 딴다. 피처 브랜치는 PR하지 않고 로컬에서 머지만 한다. origin에 푸시하는 건 `dev`, `master`만.
- 병합·푸시(요청 시): 피처 → `dev` → `master` 순서로 병합, `git push origin dev master`, 끝나면 로컬 피처 브랜치 삭제. fast-forward가 안 되면 `-m "Merge : <기능명> → dev"`로 병합한다 (기본 메시지는 `commit-msg` 훅이 거부, 제목 50자 이내).
- `dev` 병합 전 피처 브랜치에 `/code-review <강도> <브랜치>` 필수 (커밋한 뒤 돌린다. 기록하는 해시가 리뷰한 코드와 같아야 함). 단, 바뀐 파일이 모두 문서(`.md` `.txt` `.rst` `.adoc`, `apps/` 안은 앱 최상위 `README.md`·`AGENTS.md`만 문서로 봄)면 생략한다. 강도는 기본 `low`. 리뷰 전에 작업 중요도·코드 변경량·버그 위험을 파악해, 높일 필요가 있으면 강도와 이유를 제안하고 승인받은 뒤 실행한다. 실제 동작 버그만 병합을 막고, 나머지 지적은 결과만 보고한 뒤 병합한다. 버그를 고친 뒤에는 테스트 통과로 확인하고 다시 리뷰하지 않는다 (강도를 높여 리뷰한 중요한 작업은 재리뷰를 제안할 수 있음). 버그가 없거나 고쳤으면 `git rev-parse <브랜치> >> "$(git rev-parse --git-common-dir)/code-review-passed"`로 기록한다. 기록 없는 코드가 `dev`로 들어오는 경로는 `.githooks/reference-transaction`이, `dev`·`master` 푸시는 `.githooks/pre-push`가 막는다 (실수 방지용, 의도적 우회는 못 막음). `/code-review`는 `master`와 비교하므로 `dev`가 앞서 있으면 범위가 더 넓게 잡힌다.
- 리뷰 통과를 기록한 뒤 `dev` 병합 전에 `/claude-md-management:revise-claude-md`를 돌린다. 제안은 승인받은 것만 반영해 피처 브랜치에 `Docs` 커밋으로 넣는다. 넣을 게 없으면 커밋 없이 넘어간다. 병합은 이 단계 뒤에도 요청받았을 때만 한다.
- 커밋 전 바뀐 앱의 `lint`·`test` 통과. 커밋은 요청받았을 때만, 메시지는 **커밋 전에 `.gitmessage.txt`를 읽고** 그 정책대로 쓴다. 형식 `Type(scope) : 제목`, 타입 첫 글자 대문자 (예: `Feat : 로그인 기능 추가`). `-m`/`-F`는 템플릿을 거치지 않으니 직접 맞춘다. 위반 시 `.githooks/commit-msg`가 거부한다 (클론 후 1회: `git config --local commit.template ./.gitmessage.txt && git config --local core.hooksPath .githooks`).
- 도메인 로직은 `apps/api` 한 곳에만. 앱·웹은 호출·표시만 한다. 동호회원 전용 기능의 권한 검사도 API에서 한다 (화면에서 숨기는 건 보조 수단).
- 미디어: 원본은 NAS, 사용자에게 보여주는 압축본·썸네일은 Cloudflare R2. 앱은 API가 발급한 presigned URL로 R2에 직접 올린다. 영상 원본을 Cloudflare Tunnel을 거쳐 NAS로 직접 올리는 설계는 하지 않는다 (무료 플랜 100MB 요청 제한).
- `.env*` 파일은 읽지도 고치지도 않는다. 예시는 `.env.example`에만 쓴다. (Read·Edit·Write·Bash 모두 `protect-env.sh` 훅으로 차단됨. Bash는 명령에 `.env` 경로가 들어 있으면 막으니, `.env.example`만 쓰는 명령으로 나눠서 실행한다)
- 강제 푸시와 원격 `dev`·`master` 삭제는 `.githooks/pre-push`가 막는다.
- API 환경변수를 추가하면 `application.yml`·`apps/api/.env.example`·`deploy/.env.example`·`deploy/compose.yml`(environment)·`deploy/README.md` 표를 함께 고친다. `deploy/`가 아직 없으면 앞의 둘만 고치고, `deploy/`를 만들 때 그때까지 추가된 키를 모두 반영한다.
- API 응답 포맷·에러 코드는 `api-conventions` 스킬을 따른다. 임의로 새 포맷 만들지 않는다.
- 운영 서버는 메모리 8GB NAS를 다른 프로젝트와 같이 쓴다. 새 컨테이너는 `mem_limit`을 걸고, JVM은 `-Xmx`로 제한한다.
- 모르는 라이브러리 API는 추측하지 말고 `node_modules` / Gradle 캐시의 실제 타입·소스를 읽는다. Expo SDK·React Native는 버전마다 API가 바뀌니 설치된 버전 기준으로 확인한다.

## 더 읽을 곳 (필요할 때만 로드됨)

- 설계 → `docs/architecture.md` (작성 예정)
- 경로별 컨벤션 → `.claude/rules/` : `mobile.md`, `web.md`, `api.md`, `testing.md`
- 절차 → `/new-feature` (API→앱·웹 기능 구현), `/project-review` (diff 점검, 기본 `/review`와 다름), `/release` (배포 전 체크)
- 참조 → `api-conventions` 스킬 (URL · 응답 래퍼 · 에러 코드 · 페이징)
- 격리 작업 → `code-reviewer`, `test-runner` 서브에이전트 (`.claude/agents/`)
- 개인 메모 → `CLAUDE.local.md` (gitignore 됨)
