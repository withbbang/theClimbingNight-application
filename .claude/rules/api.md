---
paths:
  - "apps/api/**"
---
# apps/api — Spring Boot / Java / Gradle

- 패키지: `com.theclimbingnight.<domain>` 아래 `controller` / `service` / `repository` / `dto` / `entity`. 도메인 단위 수직 분할, 공통은 `com.theclimbingnight.common`.
- 레이어: Controller는 요청 검증·DTO 매핑·응답만. 비즈니스 로직은 Service. Repository는 Spring Data JPA 인터페이스.
- DTO: 요청 `Xxx<행위>Request`(`PostCreateRequest`, `PostUpdateRequest`), 응답 `XxxResponse`, **Java `record`** 로. Entity는 Controller 밖으로 절대 노출하지 않는다.
- 응답 래퍼·에러 코드·페이징은 `api-conventions` 스킬 그대로. 새 포맷 만들지 않는다.
- 검증: `@Valid` + `jakarta.validation`. 도메인 예외는 `BusinessException(ErrorCode)` 하나를 상속, `GlobalExceptionHandler`(`@RestControllerAdvice`) 한 곳에서 변환.
- 권한: 역할은 `USER`(일반회원) · `MEMBER`(동호회원) · `ADMIN`(운영진). 동호회원 전용 API(벙 신청·일정·참석률)는 서버에서 역할을 검사한다. 화면에서 숨기는 것만으로 막지 않는다.
- 설정: `application.yml` + 프로파일 `application-local.yml`(H2) / `application-prod.yml`(MySQL). 비밀값은 `${ENV_VAR}` 참조만, 값 하드코딩 금지.
- Lombok: `@Getter` `@Builder` `@RequiredArgsConstructor` `@NoArgsConstructor(access = PROTECTED)` 허용. **`@Data` `@Setter` 금지** (특히 entity).
- 의존성 주입은 생성자 주입만 (`@RequiredArgsConstructor`). 필드 `@Autowired` 금지.
- 트랜잭션: Service 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`.
- 동시성: 벙 정원처럼 동시에 몰리는 쓰기는 DB 유니크 제약·락으로 막는다. 애플리케이션에서 "조회 후 비교"만으로 막지 않는다.
- 테스트: Service는 Mockito 단위 테스트, Controller는 `@WebMvcTest`, Repository는 `@DataJpaTest`. `@SpringBootTest`는 꼭 필요할 때만.
- 잠금·상태 전이가 여러 서비스에 걸친 흐름(교체·되돌리기·탈퇴 등)은 Mockito와 별도로 실제 DB 테스트를 하나 둔다: `@Transactional(propagation = NOT_SUPPORTED)` + `TransactionTemplate`으로 커밋한 뒤 새로 읽어 확인한다.
- 벌크 `@Modifying` 쿼리는 `flushAutomatically = true`만 쓰고 `clearAutomatically`는 쓰지 않는다 (영속성 컨텍스트가 비워지면 그 뒤에 바꾼 엔티티 값이 저장되지 않는다).
- 시간·난수: `Clock`과 `java.security.SecureRandom`을 빈으로 등록해 주입. `LocalDateTime.now()`·`new Random()` 직접 호출 금지 → 테스트에서 고정 불가.
- 개인정보를 저장하는 테이블을 늘리면 탈퇴 처리(`UserWithdrawnEvent` 리스너)와 `apps/web/content/privacy.ts`를 함께 고친다.
- Flyway 마이그레이션은 적용된 버전을 고치지 말고 새 버전을 만든다.
- 미디어 작업(`media_jobs`)을 찾거나 취소·실패 처리할 때는 `target_id`만 보지 말고 `target_type`(POST_MEDIA, PROFILE_IMAGE)까지 조건에 넣는다. 두 테이블의 id가 겹친다.
- 미디어: 파일 본문은 API 서버를 거치지 않는다. API는 R2 presigned URL 발급과 업로드 완료 통보만 받고, 변환·원본 보관은 별도 워커가 한다.
- 운영 점검: 컨테이너 상태 확인은 `GET /api/v1/health`(`@PublicApi`, DB 안 봄). prod는 MySQL 격리 수준이 READ-COMMITTED가 아니면 기동하지 않는다 (`DatabaseSettingsCheck`).
- 탈퇴는 `AccountWithdrawalService`로 들어온다. Apple 같은 외부 호출은 사용자 행 잠금 트랜잭션(`UserService.withdraw`) 밖에서 한다.
- 메모리: 운영 API는 `-Xmx512m` + `ExitOnOutOfMemoryError`. 파일을 `byte[]`로 통째로 들고 있지 말고 스트림으로 처리한다.
- 공개 API(`@PublicApi`): `Authorization` 헤더가 없으면 비로그인으로 처리하지만, 헤더가 있는데 무효이거나 만료됐으면 공개 API도 401 `AUTH_REQUIRED`다(만료된 사용자가 비로그인으로 보이는 것을 막고, 클라이언트가 갱신 후 재시도하게 한다).
- 워커 프로필(`SPRING_PROFILES_ACTIVE=prod,worker`): 웹 서버·Flyway·`@Scheduled`가 돌지 않는다. 새 `@Scheduled` 작업은 `app.scheduling.enabled`로 묶여(`SchedulingConfig`) 워커에서 꺼지므로, 그 설정 아래에 둔다. 워커 작업은 `MediaWorkerLoop` 스레드가 처리한다.
