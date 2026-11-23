# 1단계-01 API 기반과 로그인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `apps/api`에 Spring Boot 프로젝트를 만들고 카카오·네이버·구글·Apple 소셜 로그인, 자체 JWT, 역할 기반 권한, 내 정보·탈퇴 API를 완성한다.

**Architecture:** Spring Security 없이 `HandlerInterceptor`가 `Authorization: Bearer` 액세스 토큰을 검증하고, 요청마다 DB에서 사용자를 읽어 상태(탈퇴)와 역할을 판정한다. 모든 `/api/**`는 기본적으로 인증이 필요하며 `@PublicApi`만 예외다. 소셜 토큰은 제공자별 `SocialTokenVerifier`가 검증하고(카카오·네이버는 사용자 정보 API, 구글·Apple은 JWKS로 ID 토큰 서명 검증), 처음 로그인한 사람은 가입 토큰을 받아 닉네임을 정한 뒤 가입한다.

**Tech Stack:** Spring Boot 4.1.2, Java 17, Gradle 9.8.2(wrapper), Spring Data JPA, Flyway, MySQL 8.4(운영)/H2 MySQL 모드(로컬·테스트), `com.nimbusds:nimbus-jose-jwt`, Lombok, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/architecture.md` (6장 회원과 권한, 10장 데이터 모델, 13장 결정 기록). 규약은 `.claude/rules/api.md`, `.claude/rules/testing.md`, `api-conventions` 스킬.

## 1단계 전체 계획 순서

이 문서는 8개 중 첫 번째다. 나머지는 앞 계획이 끝난 뒤, 실제로 생긴 인터페이스를 기준으로 작성한다.

1. **API 기반과 로그인** (이 문서)
2. 명부와 동호회원 인증: 명부 CSV 등록 API·스크립트, 인증 신청, 승인·일괄 승인·거절, 자격 해제, 탈퇴 시 명부 연결 해제
3. 벙: 개설·수정·취소·복제, 신청·대기·취소, 늦은 취소 표시, 내보내기, 종료 후 자동 참석 기록, 참석 보정, 블랙리스트, 명단 공개 토글
4. 참석률·공지·문의·앱 설정·푸시 알림 (Expo Push)
5. 모바일 앱 기반: Expo 프로젝트, EAS development build, 소셜 로그인 4종, 토큰 저장, API 클라이언트, 탭 구조
6. 모바일 화면: 벙·참석률·인증 신청·문의·마이페이지·운영진 메뉴
7. 웹뷰 페이지 (`apps/web`): 공지·소개·굿즈·약관, 앱 브리지
8. 배포와 출시 준비: Compose·nginx·Cloudflare Tunnel·CI·DB 백업, 스토어 심사 준비

## 작업 방식

- 브랜치: `dev`에서 `feat/api-auth`를 딴다. 태스크마다 커밋한다.
- 커밋 메시지: `.gitmessage.txt` 정책. 형식 `Feat(api) : 제목` (50자 이내), 본문 `-` 목록, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 모든 태스크 끝나면 `/code-review low feat/api-auth` → 버그만 고치고 테스트로 확인 → 리뷰 기록 → `/claude-md-management:revise-claude-md` → 병합은 요청 시 (CLAUDE.md 절차).
- 테스트 실행: `cd apps/api && ./gradlew test` (특정 클래스는 `--tests '<FQCN>'`).

## Global Constraints

- 패키지 루트 `com.theclimbingnight`. 도메인별 `controller` / `service` / `repository` / `dto` / `entity`, 공통은 `com.theclimbingnight.common`.
- 빌드 설정은 k-drinker(`../k-drinker/apps/api/build.gradle`)와 같은 버전: Spring Boot `4.1.2`, dependency-management `1.1.7`, Java toolchain `17`, Gradle wrapper `9.8.2`. `group = 'com.theclimbingnight'`.
- 응답은 `{ success, data }` / `{ success: false, error: { code, message, fields? } }` 래퍼만 쓴다. 에러 `message`는 한국어 완성 문장.
- DTO는 Java `record`. 요청 `Xxx<행위>Request`, 응답 `XxxResponse`. Entity는 Controller 밖으로 내보내지 않는다.
- `@Data`, `@Setter`, 필드 `@Autowired` 금지. 생성자 주입만.
- Service 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`.
- 시각은 `Instant`로 다루고 DB에는 UTC로 저장한다 (`spring.jpa.properties.hibernate.jdbc.time_zone: UTC`). `Instant.now()`, `LocalDateTime.now()`, `new Random()`을 직접 호출하지 않는다. `Clock`, `SecureRandom` 빈을 주입한다.
- 비밀값은 `${ENV_VAR}` 참조만 쓴다. 환경변수를 추가하면 `application.yml`과 `apps/api/.env.example`을 함께 고친다. `deploy/`는 계획 8에서 만들고 그때 반영한다.
- 테스트 이름은 한국어로 행동을 서술한다 (`만료된_토큰이면_401이다()`). given / when / then 주석, AssertJ `assertThat` 체인만. 외부 HTTP는 모킹한다.
- 토큰 수명: 액세스 30분, 리프레시 30일(회전), 가입 토큰 10분.
- 운영 JVM: `-Xmx512m -XX:+ExitOnOutOfMemoryError` (계획 8에서 Compose에 넣는다).

## Review Focus

1. **같은 소셜 계정으로 가입을 두 번 동시에 누름** → 사용자는 하나만 생기고, 두 번째 요청은 `AUTH_ALREADY_REGISTERED`(409). Task 6 테스트 `가입_중_같은_소셜계정이_먼저_저장되면_409다`.
2. **회전으로 폐기된 리프레시 토큰 재사용** → 401 `AUTH_REQUIRED`. Task 6 테스트 `이미_사용한_리프레시_토큰이면_401이다`.
3. **탈퇴 직후 아직 만료되지 않은 액세스 토큰으로 요청** → 401. 요청마다 DB 상태를 보기 때문에 막힌다. Task 4 테스트 `탈퇴한_사용자의_유효한_토큰이면_401이다`, Task 7 통합 테스트.
4. **운영진이 역할을 내린 직후 기존 토큰으로 상위 권한 API 호출** → 즉시 403. 토큰에 역할을 넣지 않고 DB에서 읽는다. Task 4 테스트 `DB의_역할이_바뀌면_토큰과_무관하게_반영된다`.
5. **닉네임 앞뒤 공백, 이모지, 길이 경계** → 공백은 잘라내고, 허용 문자 밖이면 400 `USER_NICKNAME_INVALID`. Task 6 테스트 `닉네임_규칙_경계값`.

---

### Task 1: 프로젝트 골격과 공통 응답

**Files:**
- Create: `apps/api/build.gradle`, `apps/api/settings.gradle`, `apps/api/gradlew`, `apps/api/gradlew.bat`, `apps/api/gradle/wrapper/gradle-wrapper.jar`, `apps/api/gradle/wrapper/gradle-wrapper.properties` (k-drinker에서 복사한 뒤 group 수정)
- Create: `apps/api/src/main/java/com/theclimbingnight/ApiApplication.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/common/{ApiResponse,ErrorCode,BusinessException,GlobalExceptionHandler}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/common/config/{ClockConfig,RandomConfig}.java`
- Create: `apps/api/src/main/resources/{application.yml,application-local.yml,application-prod.yml}`
- Create: `apps/api/.env.example`
- Test: `apps/api/src/test/java/com/theclimbingnight/common/GlobalExceptionHandlerTest.java`, `apps/api/src/test/java/com/theclimbingnight/ApiApplicationTests.java`

**Interfaces:**
- Produces:
  - `record ApiResponse<T>(boolean success, T data, ErrorBody error)`, `static <T> ApiResponse<T> success(T)`, `static ApiResponse<Void> failure(ErrorCode)`, `failure(ErrorCode, List<FieldReason>)`, 중첩 `ErrorBody(String code, String message, List<FieldReason> fields)`, `FieldReason(String field, String reason)`. k-drinker `com.kdrinker.common.ApiResponse`와 같은 구조.
  - `enum ErrorCode { HttpStatus getStatus(); String getMessage(); }`. 이후 태스크가 상수를 추가한다.
  - `class BusinessException extends RuntimeException { ErrorCode getErrorCode(); }`
  - 빈: `Clock`(`Clock.systemUTC()`), `SecureRandom`

- [ ] **Step 1: 빌드 파일 복사·수정**
  k-drinker의 `build.gradle`, `settings.gradle`, `gradlew*`, `gradle/wrapper/*`를 `apps/api/`로 복사한다. `group`을 `com.theclimbingnight`로 바꾼다. 의존성에서 `metadata-extractor`, `commons-csv`, `anthropic-java`를 뺀다. `com.nimbusds:nimbus-jose-jwt`를 추가한다 (Maven Central 최신 안정 10.x로 고정하고, 고른 버전을 커밋 본문에 적는다). `tasks.test`의 H2 URL을 `jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1`로 둔다.

- [ ] **Step 2: 실패하는 테스트 작성** `GlobalExceptionHandlerTest` (`@WebMvcTest` + 테스트 전용 컨트롤러 `ThrowingController`를 테스트 소스에 둔다)
  ```java
  @Test void 비즈니스_예외는_에러코드의_상태와_메시지로_응답한다()
  // GET /test/forbidden → BusinessException(AUTH_FORBIDDEN)
  // status 403, $.success=false, $.error.code="AUTH_FORBIDDEN", $.error.message="권한이 없습니다."
  @Test void 검증_실패는_필드별_사유를_담는다()
  // POST /test/valid {"name":""} (@NotBlank name) → 400, $.error.code="COMMON_VALIDATION_FAILED", $.error.fields[0].field="name"
  @Test void 없는_경로는_404_COMMON_NOT_FOUND다()
  @Test void 처리되지_않은_예외는_500이고_내부_메시지를_노출하지_않는다()
  // GET /test/boom → RuntimeException("secret") → 500, $.error.message="서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", 응답 본문에 "secret" 없음
  ```
  `ApiApplicationTests.컨텍스트가_뜬다()` (`@SpringBootTest`).

- [ ] **Step 3: 실패 확인**
  Run: `cd apps/api && ./gradlew test`
  Expected: 컴파일 실패(클래스 없음)

- [ ] **Step 4: 공통 클래스와 설정 구현**
  - `ErrorCode` 초기 상수:
    - `COMMON_VALIDATION_FAILED(400, "요청 값이 올바르지 않습니다.")`
    - `COMMON_NOT_FOUND(404, "요청한 항목을 찾을 수 없습니다.")`
    - `COMMON_INTERNAL_ERROR(500, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")`
    - `AUTH_REQUIRED(401, "로그인이 필요합니다.")`
    - `AUTH_FORBIDDEN(403, "권한이 없습니다.")`
  - `GlobalExceptionHandler`는 k-drinker 것을 가져오되, `MaxUploadSizeExceededException` 분기는 `COMMON_VALIDATION_FAILED` 하나로 줄인다.
  - `application.yml`:
    - `spring.application.name: theclimbingnight-api`, `profiles.default: local`, `jpa.open-in-view: false`, `jpa.properties.hibernate.jdbc.time_zone: UTC`
    - `app.auth` 블록(Task 3·5에서 채움)
  - `application-local.yml`: H2 파일 DB `jdbc:h2:file:./.data/theclimbingnight;MODE=MySQL`, `ddl-auto: validate`.
  - `application-prod.yml`: `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, `ddl-auto: validate`.

- [ ] **Step 5: 통과 확인**
  Run: `cd apps/api && ./gradlew test`
  Expected: BUILD SUCCESSFUL, 5개 통과

- [ ] **Step 6: 커밋** `Feat(api) : Spring Boot 프로젝트와 공통 응답 추가`

---

### Task 2: 회원 스키마와 엔티티

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V1__users_auth.sql`
- Create: `apps/api/src/main/java/com/theclimbingnight/user/entity/{User,Role,UserStatus}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/user/repository/UserRepository.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/entity/{AuthIdentity,RefreshToken}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/social/SocialProvider.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/repository/{AuthIdentityRepository,RefreshTokenRepository}.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/user/repository/UserRepositoryTest.java`, `apps/api/src/test/java/com/theclimbingnight/auth/repository/AuthIdentityRepositoryTest.java`

**Interfaces:**
- Produces:
  - `enum Role { USER, MEMBER, ADMIN; boolean atLeast(Role required) }` (선언 순서로 비교)
  - `enum UserStatus { ACTIVE, WITHDRAWN }`
  - `enum SocialProvider { KAKAO, NAVER, GOOGLE, APPLE }`
  - `User`: `Long getId()`, `String getNickname()` (탈퇴 시 null), `Role getRole()`, `UserStatus getStatus()`, `static User create(String nickname)` (role USER, ACTIVE), `void changeNickname(String)`, `void withdraw(Instant now)` (status WITHDRAWN, withdrawnAt=now, nickname=null), `boolean isActive()`
  - `AuthIdentity`: `static AuthIdentity link(User user, SocialProvider provider, String providerUserId)`, `User getUser()`
  - `RefreshToken`: `static RefreshToken issue(long userId, String tokenHash, Instant expiresAt)`, `boolean isUsable(Instant now)`, `void revoke(Instant now)`, `long getUserId()`
  - `UserRepository extends JpaRepository<User, Long>`: `boolean existsByNicknameIgnoreCase(String nickname)`
  - `AuthIdentityRepository`: `Optional<AuthIdentity> findByProviderAndProviderUserId(SocialProvider, String)`, `void deleteByUserId(Long userId)`
  - `RefreshTokenRepository`: `Optional<RefreshToken> findByTokenHash(String)`, `@Modifying int revokeAllActiveByUserId(long userId, Instant now)`

- [ ] **Step 1: 마이그레이션 작성** `V1__users_auth.sql`
  - 공통: 모든 테이블에 `created_at DATETIME(6) NOT NULL`, `updated_at DATETIME(6) NOT NULL`. 엔티티에서 `@PrePersist`/`@PreUpdate`로 채운다 (주입한 Clock이 아니라 DB 기록용이라 허용).
  - `users(id BIGINT PK AUTO_INCREMENT, nickname VARCHAR(16) NULL UNIQUE, role VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, withdrawn_at DATETIME(6) NULL)`
  - `auth_identities(id, user_id BIGINT NOT NULL FK users, provider VARCHAR(16) NOT NULL, provider_user_id VARCHAR(255) NOT NULL, UNIQUE(provider, provider_user_id))`
  - `refresh_tokens(id, user_id BIGINT NOT NULL FK users, token_hash CHAR(64) NOT NULL UNIQUE, expires_at DATETIME(6) NOT NULL, revoked_at DATETIME(6) NULL)`, `INDEX(user_id)`
  - ENUM 컬럼은 `VARCHAR` + `@Enumerated(EnumType.STRING)`로 둔다 (MySQL ENUM은 값 추가 때마다 ALTER가 필요하므로).

- [ ] **Step 2: 실패하는 테스트 작성** (`@DataJpaTest`)
  ```java
  // UserRepositoryTest
  @Test void 닉네임은_중복될_수_없다()               // 같은 닉네임 두 명 saveAndFlush → DataIntegrityViolationException
  @Test void 탈퇴한_사용자는_여럿이어도_닉네임_충돌이_없다() // 두 명 withdraw 후 flush → 예외 없음
  @Test void 닉네임_존재_확인은_대소문자를_무시한다()     // "Climber" 저장 → existsByNicknameIgnoreCase("climber") == true
  // AuthIdentityRepositoryTest
  @Test void 같은_제공자의_같은_사용자ID는_한번만_연결된다() // KAKAO "123" 두 번 → DataIntegrityViolationException
  @Test void 제공자가_다르면_같은_ID도_연결된다()          // KAKAO "123", NAVER "123" → 성공
  ```

- [ ] **Step 3: 실패 확인** Run: `cd apps/api && ./gradlew test --tests 'com.theclimbingnight.*Repository*'` → Expected: 컴파일 실패

- [ ] **Step 4: 엔티티·리포지토리 구현** Lombok은 `@Getter`, `@NoArgsConstructor(access = PROTECTED)`만 쓴다. 상태 변경은 위 메서드로만 한다.

- [ ] **Step 5: 통과 확인** 같은 명령 → 5개 통과, `ddl-auto: validate`로 스키마 일치

- [ ] **Step 6: 커밋** `Feat(api) : 회원·소셜 연결·리프레시 토큰 스키마 추가`

---

### Task 3: JWT 발급과 검증

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/AuthProperties.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/social/SocialIdentity.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/service/JwtTokenService.java`
- Modify: `apps/api/src/main/java/com/theclimbingnight/common/ErrorCode.java`, `application.yml`, `apps/api/.env.example`
- Test: `apps/api/src/test/java/com/theclimbingnight/auth/service/JwtTokenServiceTest.java`

**Interfaces:**
- Consumes: `Clock` 빈, `SocialProvider` (Task 2)
- Produces:
  - `@ConfigurationProperties("app.auth") record AuthProperties(String jwtSecret, Duration accessTtl, Duration refreshTtl, Duration signupTtl, Provider kakao, Provider naver, Google google, Apple apple)`. 중첩 `Provider(String baseUrl)`, `Google(List<String> clientIds, String jwksUrl)`, `Apple(List<String> audiences, String jwksUrl)`.
  - `record SocialIdentity(SocialProvider provider, String providerUserId)`
  - `JwtTokenService`:
    - `String issueAccessToken(long userId)`
    - `Instant accessTokenExpiresAt()`: 지금 발급하면 언제 만료되는지
    - `long parseAccessToken(String token)`: 실패하면 `AUTH_REQUIRED`
    - `String issueSignupToken(SocialIdentity identity)`
    - `SocialIdentity parseSignupToken(String token)`: 실패하면 `AUTH_SIGNUP_EXPIRED`
  - `ErrorCode.AUTH_SIGNUP_EXPIRED(400, "가입 시간이 지났습니다. 처음부터 다시 로그인해 주세요.")`

- [ ] **Step 1: 실패하는 테스트 작성** (`Clock.fixed`와 `MutableClock` 테스트 헬퍼로 시간을 옮긴다)
  ```java
  @Test void 발급한_액세스_토큰에서_사용자ID를_꺼낸다()          // issue(42) → parse == 42
  @Test void 액세스_토큰은_30분_직전까지_유효하다()             // +29m59s → 42
  @Test void 액세스_토큰은_30분이_지나면_401이다()              // +30m1s → BusinessException AUTH_REQUIRED
  @Test void 가입_토큰은_액세스_토큰으로_쓸_수_없다()            // parseAccessToken(signupToken) → AUTH_REQUIRED
  @Test void 액세스_토큰은_가입_토큰으로_쓸_수_없다()            // parseSignupToken(access) → AUTH_SIGNUP_EXPIRED
  @Test void 가입_토큰은_10분이_지나면_AUTH_SIGNUP_EXPIRED다()
  @Test void 서명이_바뀐_토큰은_401이다()                     // 마지막 글자 변경
  @Test void 비밀키가_32바이트보다_짧으면_생성에_실패한다()        // IllegalStateException
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests '*JwtTokenServiceTest'` → 컴파일 실패

- [ ] **Step 3: 구현**
  - HS256, 클레임 `sub`(userId 또는 `provider:providerUserId`), `typ`(`access`/`signup`), `iat`, `exp`.
  - **만료 판정은 주입한 Clock으로 직접 한다.** nimbus 기본 클레임 검증기는 시스템 시계를 쓰기 때문이다.
  - `application.yml`의 `app.auth`:
    - `jwt-secret: ${JWT_SECRET}`, `access-ttl: 30m`, `refresh-ttl: 30d`, `signup-ttl: 10m`
    - `kakao.base-url: https://kapi.kakao.com`, `naver.base-url: https://openapi.naver.com`
    - `google.client-ids: ${GOOGLE_CLIENT_IDS:}` (쉼표 구분), `google.jwks-url: https://www.googleapis.com/oauth2/v3/certs`
    - `apple.audiences: ${APPLE_AUDIENCES:}`, `apple.jwks-url: https://appleid.apple.com/auth/keys`
  - `.env.example`에 키만 추가: `JWT_SECRET=`, `GOOGLE_CLIENT_IDS=`, `APPLE_AUDIENCES=`
  - 테스트 프로필(`src/test/resources/application.yml`)에는 32바이트 이상의 고정 테스트 비밀값을 둔다.

- [ ] **Step 4: 통과 확인** 같은 명령 → 8개 통과

- [ ] **Step 5: 커밋** `Feat(api) : 액세스·가입 JWT 발급과 검증 추가`

---

### Task 4: 인증 인터셉터와 역할 권한

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/web/{PublicApi,RequireRole,CurrentUser,AuthenticatedUser,AuthInterceptor,CurrentUserArgumentResolver}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/common/config/WebConfig.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/auth/web/AuthInterceptorTest.java` (테스트 전용 컨트롤러 `ProbeController`)

**Interfaces:**
- Consumes: `JwtTokenService.parseAccessToken`, `UserRepository.findById`, `Role.atLeast`
- Produces:
  - `@PublicApi`: 메서드·클래스에 붙인다. 토큰이 없어도 통과한다. 토큰이 있고 유효하면 사용자를 채운다.
  - `@RequireRole(Role value)`: 메서드·클래스에 붙인다. `user.role().atLeast(value)`가 아니면 `AUTH_FORBIDDEN`.
  - `record AuthenticatedUser(long id, Role role)`
  - 컨트롤러 파라미터 `@CurrentUser AuthenticatedUser user`. `@PublicApi`에서 토큰이 없으면 null.
  - 규칙: `/api/**`의 모든 핸들러는 `@PublicApi`가 없으면 인증이 필요하다. 헤더 형식은 `Authorization: Bearer <token>`.

- [ ] **Step 1: 실패하는 테스트 작성** (`@WebMvcTest(ProbeController.class)` + `@Import(WebConfig, AuthInterceptor 관련, JwtTokenService)`, `UserRepository`는 `@MockitoBean`)
  ```java
  @Test void 토큰이_없으면_401이다()                         // GET /api/probe/user → 401 AUTH_REQUIRED
  @Test void Bearer가_아닌_헤더는_401이다()                   // "Token abc"
  @Test void 유효한_토큰이면_사용자ID와_역할이_주입된다()         // 응답 본문에 id=7, role=USER
  @Test void 탈퇴한_사용자의_유효한_토큰이면_401이다()           // 사용자 status WITHDRAWN
  @Test void 없는_사용자의_토큰이면_401이다()
  @Test void USER가_MEMBER_전용을_호출하면_403이다()           // GET /api/probe/member → 403 AUTH_FORBIDDEN
  @Test void ADMIN은_MEMBER_전용도_호출할_수_있다()             // 200
  @Test void DB의_역할이_바뀌면_토큰과_무관하게_반영된다()        // 같은 토큰, 저장소가 MEMBER→USER로 바뀐 사용자 반환 → 403
  @Test void 공개_API는_토큰_없이_200이고_사용자는_null이다()
  @Test void 공개_API에_잘못된_토큰을_보내도_200이고_사용자는_null이다()
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests '*AuthInterceptorTest'` → 컴파일 실패

- [ ] **Step 3: 구현**
  - `AuthInterceptor implements HandlerInterceptor`: `HandlerMethod`가 아니면 통과시킨다. 인증 결과를 request attribute(`AuthenticatedUser`)에 둔다. 예외는 `BusinessException`으로 던져 `GlobalExceptionHandler`가 변환하게 한다.
  - `WebConfig implements WebMvcConfigurer`: 인터셉터를 `/api/**`에 등록하고, 인자 리졸버를 추가한다.

- [ ] **Step 4: 통과 확인** 같은 명령 → 10개 통과

- [ ] **Step 5: 커밋** `Feat(api) : 토큰 인증 인터셉터와 역할 권한 추가`

---

### Task 5: 소셜 토큰 검증기

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/social/{SocialTokenVerifier,SocialTokenVerifiers,KakaoTokenVerifier,NaverTokenVerifier,IdTokenVerifier,GoogleTokenVerifier,AppleTokenVerifier}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/social/SocialConfig.java` (RestClient, JWKSource 빈)
- Modify: `ErrorCode.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/auth/social/{KakaoTokenVerifierTest,NaverTokenVerifierTest,GoogleTokenVerifierTest,AppleTokenVerifierTest}.java`

**Interfaces:**
- Consumes: `AuthProperties`, `SocialIdentity`, `SocialProvider`, `Clock`
- Produces:
  - `interface SocialTokenVerifier { SocialProvider provider(); SocialIdentity verify(String token); }`
  - `SocialTokenVerifiers`: `SocialTokenVerifier get(SocialProvider provider)` (모든 `SocialTokenVerifier` 빈을 provider로 묶음)
  - `ErrorCode.AUTH_INVALID_SOCIAL_TOKEN(401, "소셜 로그인 정보를 확인하지 못했습니다. 다시 로그인해 주세요.")`
  - `ErrorCode.AUTH_PROVIDER_UNAVAILABLE(503, "로그인 서비스에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요.")`

제공자별 규칙 (spec 6장):

| 제공자 | 토큰 | 검증 | 사용자 ID |
|---|---|---|---|
| KAKAO | access token | `GET {kakao.base-url}/v2/user/me`, `Authorization: Bearer` | 응답 `id` (숫자 → 문자열) |
| NAVER | access token | `GET {naver.base-url}/v1/nid/me`, `Authorization: Bearer` | `resultcode == "00"`일 때 `response.id` |
| GOOGLE | ID token | RS256, JWKS `google.jwks-url`, `iss` ∈ {`accounts.google.com`, `https://accounts.google.com`}, `aud` ∈ `google.client-ids`, `exp` > Clock 현재 | `sub` |
| APPLE | ID token | RS256, JWKS `apple.jwks-url`, `iss` == `https://appleid.apple.com`, `aud` ∈ `apple.audiences`, `exp` > Clock 현재 | `sub` |

- 제공자가 4xx로 응답하거나 결과 코드가 실패면 `AUTH_INVALID_SOCIAL_TOKEN`. 5xx나 타임아웃이면 `AUTH_PROVIDER_UNAVAILABLE`.
- 구글과 Apple은 공통 로직을 `IdTokenVerifier`(JWKSource, 허용 iss 목록, 허용 aud 목록, Clock)에 두고, 두 클래스는 설정값만 넘긴다.
- 운영용 JWKSource는 nimbus의 캐시되는 원격 JWK 소스를 쓴다. 테스트는 생성한 RSA 키로 만든 `ImmutableJWKSet`을 넣는다.

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // KakaoTokenVerifierTest (MockRestServiceServer)
  @Test void 응답의_id를_사용자ID로_돌려준다()           // {"id":1234567} → SocialIdentity(KAKAO,"1234567"), 요청 헤더 Authorization: Bearer tok
  @Test void 제공자가_401이면_AUTH_INVALID_SOCIAL_TOKEN이다()
  @Test void 제공자가_500이면_AUTH_PROVIDER_UNAVAILABLE이다()
  // NaverTokenVerifierTest
  @Test void resultcode가_00이면_response_id를_돌려준다()
  @Test void resultcode가_00이_아니면_AUTH_INVALID_SOCIAL_TOKEN이다()   // {"resultcode":"024","message":"Authentication failed"}
  // GoogleTokenVerifierTest (테스트 RSA 키, 허용 aud "ios-client")
  @Test void 유효한_ID토큰이면_sub을_돌려준다()
  @Test void 두_가지_iss_형식을_모두_허용한다()
  @Test void aud가_다르면_401이다()
  @Test void 만료된_토큰이면_401이다()                  // exp = Clock 현재 - 1초
  @Test void 다른_키로_서명한_토큰이면_401이다()
  // AppleTokenVerifierTest
  @Test void 유효한_ID토큰이면_sub을_돌려준다()
  @Test void iss가_apple이_아니면_401이다()
  @Test void 허용_aud_목록이_비어_있으면_모든_토큰을_거부한다()   // 설정 누락 시 열려 버리지 않게
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests 'com.theclimbingnight.auth.social.*'` → 컴파일 실패

- [ ] **Step 3: 구현** RestClient는 `spring-boot-starter-restclient`로 만들고 연결 5초, 읽기 5초 타임아웃을 둔다.

- [ ] **Step 4: 통과 확인** 같은 명령 → 13개 통과

- [ ] **Step 5: 커밋** `Feat(api) : 카카오·네이버·구글·Apple 토큰 검증 추가`

---

### Task 6: 로그인·가입·토큰 갱신·로그아웃 API

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/service/{AuthService,RefreshTokens,LoginResult,TokenPair}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/user/service/NicknamePolicy.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/controller/AuthController.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/auth/dto/{LoginRequest,LoginResponse,SignupRequest,RefreshRequest,LogoutRequest,TokenResponse}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/user/dto/UserResponse.java`
- Modify: `ErrorCode.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/auth/service/AuthServiceTest.java`, `apps/api/src/test/java/com/theclimbingnight/user/service/NicknamePolicyTest.java`, `apps/api/src/test/java/com/theclimbingnight/auth/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: Task 2 리포지토리·엔티티, Task 3 `JwtTokenService`, Task 5 `SocialTokenVerifiers`, `SecureRandom`, `Clock`
- Produces:
  - `NicknamePolicy.normalize(String raw) → String`: 앞뒤 공백을 제거한다. 2~16자, `^[가-힣a-zA-Z0-9_]+$`가 아니면 `USER_NICKNAME_INVALID`.
  - `record TokenPair(String accessToken, Instant accessTokenExpiresAt, String refreshToken, User user)`
  - `sealed interface LoginResult permits LoggedIn, SignupRequired`. `LoggedIn(TokenPair tokens)`, `SignupRequired(String signupToken)`
  - `AuthService`:
    - `LoginResult login(SocialProvider provider, String socialToken)`
    - `TokenPair signup(String signupToken, String nickname)`
    - `TokenPair refresh(String refreshToken)`
    - `void logout(String refreshToken)`
    - `TokenPair issueTokens(User user)`
  - `RefreshTokens`: `String newToken()` (SecureRandom 32바이트, base64url 패딩 없음), `static String hash(String token)` (SHA-256 hex 64자)
  - `record UserResponse(long id, String nickname, Role role)`. 닉네임이 null이면 `"탈퇴한 사용자"`. Task 7과 이후 계획이 재사용한다.
  - 엔드포인트 (모두 `@PublicApi`):

    | 메서드·경로 | 요청 | 응답 `data` |
    |---|---|---|
    | `POST /api/v1/auth/login` | `{ provider, token }` | `{ status: "LOGGED_IN", tokens: TokenResponse }` 또는 `{ status: "SIGNUP_REQUIRED", signupToken }` |
    | `POST /api/v1/auth/signup` (201) | `{ signupToken, nickname }` | `TokenResponse` |
    | `POST /api/v1/auth/refresh` | `{ refreshToken }` | `TokenResponse` |
    | `POST /api/v1/auth/logout` (204) | `{ refreshToken }` | 없음 |

    `TokenResponse(accessToken, accessTokenExpiresAt, refreshToken, UserResponse user)`
  - 새 ErrorCode:
    - `USER_NICKNAME_INVALID(400, "닉네임은 2~16자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다.")`
    - `USER_NICKNAME_TAKEN(409, "이미 사용 중인 닉네임입니다.")`
    - `AUTH_ALREADY_REGISTERED(409, "이미 가입된 계정입니다. 다시 로그인해 주세요.")`

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // NicknamePolicyTest
  @Test void 닉네임_규칙_경계값()
  // "가" → INVALID, "가나" → "가나", 16자 → 통과, 17자 → INVALID, "  홍길동 " → "홍길동",
  // "클라이머🧗" → INVALID, "a b" → INVALID, "climb_er1" → 통과
  // AuthServiceTest (Mockito, 고정 Clock)
  @Test void 연결된_소셜계정이면_토큰을_발급한다()               // LoggedIn, refresh 해시 저장 1회
  @Test void 처음_보는_소셜계정이면_가입_토큰을_돌려준다()          // SignupRequired, parseSignupToken == 원래 identity
  @Test void 가입하면_USER로_만들고_소셜계정을_연결한다()
  @Test void 가입_때_닉네임이_사용중이면_409다()                 // USER_NICKNAME_TAKEN
  @Test void 가입_중_같은_소셜계정이_먼저_저장되면_409다()         // 연결 저장 시 DataIntegrityViolationException + 재조회하면 존재 → AUTH_ALREADY_REGISTERED
  @Test void 리프레시하면_새_토큰을_주고_이전_토큰은_폐기한다()
  @Test void 이미_사용한_리프레시_토큰이면_401이다()
  @Test void 만료된_리프레시_토큰이면_401이다()                 // 30일 + 1초
  @Test void 탈퇴한_사용자의_리프레시_토큰이면_401이다()
  @Test void 로그아웃하면_리프레시_토큰이_폐기된다()
  @Test void 없는_리프레시_토큰으로_로그아웃해도_204다()           // 멱등
  // AuthControllerTest (@WebMvcTest)
  @Test void 로그인_응답_형식()               // $.data.status, $.data.tokens.accessToken, $.data.tokens.user.role
  @Test void provider가_없으면_400이다()       // COMMON_VALIDATION_FAILED, fields[0].field="provider"
  @Test void 가입은_201이다()
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests '*AuthServiceTest' --tests '*NicknamePolicyTest' --tests '*AuthControllerTest'` → 컴파일 실패

- [ ] **Step 3: 구현**
  - 가입 시 닉네임 중복은 먼저 `existsByNicknameIgnoreCase`로 확인한다. 저장 중 `DataIntegrityViolationException`이 나면, 소셜 연결이 이미 있으면 `AUTH_ALREADY_REGISTERED`, 없으면 `USER_NICKNAME_TAKEN`으로 바꾼다.
  - 이메일 등 제공자가 주는 다른 정보는 저장하지 않는다 (spec: 이메일 자동 병합 금지).

- [ ] **Step 4: 통과 확인** 같은 명령 → 전부 통과

- [ ] **Step 5: 커밋** `Feat(api) : 소셜 로그인·가입·토큰 갱신 API 추가`

---

### Task 7: 내 정보·닉네임 변경·탈퇴와 통합 흐름

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/user/service/{UserService,UserWithdrawnEvent}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/user/controller/UserController.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/user/dto/NicknameUpdateRequest.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/user/service/UserServiceTest.java`, `apps/api/src/test/java/com/theclimbingnight/AuthFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `UserResponse`, `NicknamePolicy`, `AuthIdentityRepository.deleteByUserId`, `RefreshTokenRepository.revokeAllActiveByUserId`, `@CurrentUser`
- Produces:
  - `UserService`: `UserResponse getMe(long userId)`, `UserResponse changeNickname(long userId, String nickname)`, `void withdraw(long userId)`
  - `record UserWithdrawnEvent(long userId)`: 탈퇴 트랜잭션 안에서 `ApplicationEventPublisher`로 발행한다. **계획 2의 명부 연결 해제가 이 이벤트를 구독한다.**
  - 엔드포인트 (인증 필요):

    | 메서드·경로 | 요청 | 응답 |
    |---|---|---|
    | `GET /api/v1/users/me` | | 200 `UserResponse` |
    | `PATCH /api/v1/users/me` | `{ nickname }` | 200 `UserResponse` |
    | `DELETE /api/v1/users/me` | | 204 |

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // UserServiceTest
  @Test void 닉네임을_바꾼다()
  @Test void 같은_닉네임으로_바꾸면_그대로_성공한다()   // 자기 자신과는 충돌로 보지 않음
  @Test void 다른_사람이_쓰는_닉네임이면_409다()
  @Test void 탈퇴하면_상태와_닉네임을_지우고_소셜연결과_리프레시토큰을_정리한다()
  // withdraw → status WITHDRAWN, nickname null, deleteByUserId(7) 호출, revokeAllActiveByUserId(7, now) 호출, UserWithdrawnEvent(7) 발행
  // AuthFlowIntegrationTest (@SpringBootTest + @AutoConfigureMockMvc, KAKAO 검증기를 토큰 문자열을 그대로 ID로 쓰는 테스트 빈으로 교체)
  @Test void 가입부터_탈퇴_후_재가입까지()
  // login(KAKAO,"u1") → SIGNUP_REQUIRED → signup("클라이머") 201 → GET /users/me 200 nickname=클라이머 role=USER
  // → DELETE /users/me 204 → 같은 액세스 토큰으로 GET /users/me → 401
  // → login(KAKAO,"u1") → SIGNUP_REQUIRED (탈퇴하면 연결이 지워져 새 계정)
  // → signup("클라이머") 201 (탈퇴자 닉네임은 다시 쓸 수 있음)
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests '*UserServiceTest' --tests '*AuthFlowIntegrationTest'` → 컴파일 실패

- [ ] **Step 3: 구현** (위 Interfaces 그대로)

- [ ] **Step 4: 전체 테스트 통과 확인** Run: `cd apps/api && ./gradlew test` → BUILD SUCCESSFUL

- [ ] **Step 5: 수동 확인** Run: `cd apps/api && JWT_SECRET=<32바이트 이상 아무 값> ./gradlew bootRun`, 다른 터미널에서 `GET http://localhost:8080/api/v1/users/me` → 401 JSON `{"success":false,"error":{"code":"AUTH_REQUIRED",...}}`

- [ ] **Step 6: 커밋** `Feat(api) : 내 정보·닉네임 변경·탈퇴 API 추가`

---

## 이 계획이 끝나면 되는 것

- 앱(계획 5)이 붙을 수 있는 로그인 API 4종과 토큰 갱신이 완성된다.
- 이후 모든 API가 `@PublicApi`, `@RequireRole`, `@CurrentUser`로 권한을 선언한다.
- 아직 없는 것: 명부·동호회원 승인(계획 2). 그 전까지 MEMBER·ADMIN 지정은 DB에서 직접 `UPDATE users SET role=...` 한다.
