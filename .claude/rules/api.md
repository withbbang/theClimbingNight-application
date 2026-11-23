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
- 시간·난수: `Clock`과 `java.security.SecureRandom`을 빈으로 등록해 주입. `LocalDateTime.now()`·`new Random()` 직접 호출 금지 → 테스트에서 고정 불가.
- Flyway 마이그레이션은 적용된 버전을 고치지 말고 새 버전을 만든다.
- 미디어: 파일 본문은 API 서버를 거치지 않는다. API는 R2 presigned URL 발급과 업로드 완료 통보만 받고, 변환·원본 보관은 별도 워커가 한다.
- 메모리: 운영 API는 `-Xmx512m` + `ExitOnOutOfMemoryError`. 파일을 `byte[]`로 통째로 들고 있지 말고 스트림으로 처리한다.
