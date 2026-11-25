# Spring Boot 예제 프로젝트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 모든 구현은 `superpowers:test-driven-development`를 따른다.

**Goal:** REST·GraphQL·gRPC가 같은 도메인과 서비스 계층을 공유하는, 학습·템플릿·가이드용 Spring Boot 멀티모듈 예제를 만든다.

**Architecture:** `core`(엔티티, JPA, QueryDSL, 서비스, JWT)에 `api-rest`, `api-graphql`, `api-grpc` 세 앱이 단방향으로 의존한다. 어댑터는 서비스만 호출하는 얇은 계층이다. JWT는 자체 발급하며 같은 토큰을 HTTP 헤더와 gRPC 메타데이터에 쓴다.

**Tech Stack:** Spring Boot 4.2.0, Java 21, Gradle Groovy, Spring Security(oauth2-jose), Spring Data JPA, QueryDSL, Flyway, PostgreSQL, springdoc-openapi, spring-graphql, Spring Boot 내장 gRPC 스타터(`spring-boot-starter-grpc-server`), Testcontainers, Checkstyle, Jib, Lombok, Spring Batch, Spring AOP, Actuator + Micrometer + Prometheus.

**Spec:** `docs/superpowers/specs/2026-11-25-spring-boot-example-design.md`
**Requirements:** `docs/requirements/2026-11-25/01/INITIAL-REQUIREMENTS.md`

## Global Constraints

- Spring Boot 4.2.0, Java 21, Gradle Groovy DSL (`build.gradle`, `settings.gradle`).
- 모듈: `core`, `api-rest`, `api-graphql`, `api-grpc`, `batch`. 의존은 `api-*`, `batch` → `core` 단방향.
- 패키지 루트 `com.benggri.example` (`.core`, `.rest`, `.graphql`, `.grpc`). 도메인은 `Member` 1:N `Post`.
- 함수 60라인 이하, 클래스 350라인 이하, 한 함수 한 역할. Checkstyle(`MethodLength` 60, `FileLength` 350)로 강제한다.
- 인증은 자체 발급 JWT(access/refresh), 역할 `USER`/`ADMIN`. 시크릿은 환경 변수 `JWT_SECRET`, 코드·yml에 기본값 하드코딩 금지.
- DB는 PostgreSQL. 실행은 docker-compose, 테스트는 Testcontainers.
- 시큐어 코딩: SQL은 파라미터 바인딩(QueryDSL/JPA)만, 비밀번호는 BCrypt, 응답에 해시·시크릿 노출 금지.
- 회원 조회·수정·삭제는 본인 또는 ADMIN만 가능하다(타인 이메일 조회 차단, 사용자 결정). 모든 어댑터(REST, GraphQL, gRPC)가 같은 서비스 규칙을 쓴다.
- 모듈별 `README.md`에 "왜 이렇게 했는가"를 기록한다.
- Spring AOP는 `core` 서비스 계층에만 적용한다(`ServiceLoggingAspect`, `AuditAspect`, `@Audited`). 로그에 메서드 인자 값을 남기지 않는다.
- 모니터링: 각 api 앱은 Actuator를 별도 management 포트로 노출하고 `health`, `prometheus`만 노출한다(나머지 엔드포인트 비노출). 메트릭 태그는 저카디널리티(class, method, outcome)만 쓰고 인자·사용자 식별자는 태그로 쓰지 않는다.
- 개발 방식은 Spec-driven + Subagent-driven. 요구사항 변경은 요구사항 → spec → 계획 순서로 문서를 먼저 갱신한다. `docs/ai-dlc/GUIDE.md`가 이를 설명한다.
- getter/setter는 직접 작성하지 않고 Lombok(`@Getter`, 필요한 곳에만 `@Setter`)을 쓴다. 엔티티에는 `@Setter` 금지(상태 변경은 도메인 메서드). record에는 Lombok 불필요. 생성자 계열 Lombok 어노테이션은 쓰지 않는다.
- 패키지 구조: 기능별 폴더 안에 단수형 계층 폴더 `entity`(엔티티·enum), `repository`, `service`, `dto`를 둔다(`core/<member|post>/...`). `security`·`aop`·`common`·`config`는 평평하게 둔다. 어댑터 모듈도 `controller`, `dto`, `config` 등 단수형 폴더를 쓴다.
- 앱 모듈(`api-*`)은 `core`가 다른 패키지 트리라서 `@SpringBootApplication(scanBasePackages = "com.benggri.example")`와 `@EntityScan("com.benggri.example.core")`, `@EnableJpaRepositories("com.benggri.example.core")`로 `core`를 스캔해야 한다.
- 포트 표(충돌 방지): api-rest 8080, api-graphql 8090, gRPC 서버 9190(Prometheus 9090·Postgres 5432와 겹치지 않게 고정), management 포트 rest 8081 / graphql 8082 / grpc 8083. `api-grpc`가 actuator용 서블릿 컨테이너를 쓰면 `server.port`가 기본 8080이 되지 않도록 명시한다(8080은 api-rest 전용).
- Docker 이미지는 Jib Gradle 플러그인으로만 빌드한다(Dockerfile 금지). 대상은 `api-rest`, `api-graphql`, `api-grpc`, `batch`. `JWT_SECRET`·DB 비밀번호는 이미지에 포함하지 않고 실행 시 환경 변수로 주입한다.

## TDD Workflow (모든 Task에 적용)

1. **Red:** 테스트를 먼저 쓰고 실행해 기대한 이유로 실패하는 것을 확인한다(컴파일 실패는 시그니처만 먼저 만든 뒤 재실행).
2. **Green:** 통과시키는 최소 구현만 쓴다. 테스트에 없는 동작은 추가하지 않는다(YAGNI).
3. **Refactor:** 통과 상태에서만 정리하고 `./gradlew check`(Checkstyle 포함)를 통과시킨다.
4. **Commit:** Green 상태에서만 커밋한다. 테스트와 구현은 같은 커밋.
- 계층: 서비스·JWT는 단위, 리포지토리는 `@DataJpaTest` + Testcontainers, 어댑터는 슬라이스(`@WebMvcTest`, GraphQL 테스터), 인증 흐름은 통합, gRPC는 in-process 서버.
- 버그가 나오면 재현하는 실패 테스트부터 쓴다.

## Review Focus

- 만료·변조·`refresh` 토큰을 access 자리에 쓴 JWT → 401 / `UNAUTHENTICATED`.
- 이미 있는 이메일로 가입 → 409 / `ALREADY_EXISTS`, 500 아님.
- `USER`가 남의 게시글 수정·삭제 → 403 / `PERMISSION_DENIED`.
- 검색 `size`가 과도하게 큼(예: 10000) → 100으로 제한, 음수 page → 400.
- `/actuator/env` 같은 비노출 엔드포인트와 메인 포트의 `/actuator/prometheus` 접근 → 404/401, 메트릭 라벨에 비밀번호·이메일 없음.
- 서비스가 예외를 던져도 AOP가 예외를 삼키거나 바꾸지 않고, 로그에 비밀번호 원문이 남지 않음.
- 키워드가 빈 문자열이거나 `%`, `_` 포함 → 전체 조회 또는 리터럴 검색, 오류 없음.

## File Structure

```
settings.gradle, build.gradle, gradle.properties, docker-compose.yml, .gitignore
config/checkstyle/checkstyle.xml
core/    src/main/java/com/benggri/example/core/{common,config,security,aop} 와 {member,post}/{entity,repository,service,dto}
         src/main/resources/db/migration/V1__init.sql
         src/test/java/...  (Testcontainers 지원: support/PostgresTestBase)
monitoring/prometheus.yml
batch/       .../batch/{config,job,step,dto} + README.md
api-rest/    .../rest/{config,auth,member,post,error}   + README.md
api-graphql/ .../graphql/{config,member,post} + resources/graphql/schema.graphqls + README.md
api-grpc/    .../grpc/{config,member,post,auth}  + src/main/proto/*.proto + README.md
README.md (루트), CLAUDE.md
```

### Task 1: 빌드 골격과 호환성 검증

**Files:** Create `settings.gradle`, `build.gradle`, `gradle.properties`, `core/build.gradle`, `api-*/build.gradle`, `docker-compose.yml`, `config/checkstyle/checkstyle.xml`, `.gitignore`(`.claude/tmp/`, `build/`, `.gradle/` 포함). Test: `core/src/test/java/com/benggri/example/core/SmokeTest.java`.

**Interfaces:** Produces: 4개 모듈 빌드, 루트 `./gradlew check`가 Checkstyle 포함, `PostgresTestBase`(Testcontainers `PostgreSQLContainer`를 `@ServiceConnection`으로 제공하는 추상 클래스, `core` 테스트 fixtures로 노출되어 `api-*`가 재사용).

- [ ] **Step 1: Red.** `SmokeTest.contextLoads()`(`@SpringBootTest` + `PostgresTestBase`, `core`는 라이브러리라 테스트 소스에 `@SpringBootApplication` 클래스 `CoreTestApplication`을 둔다)를 쓰고 실행한다. Run: `./gradlew :core:test` → Expected: FAIL(빌드 파일 없음).
- [ ] **Step 2: 의존성 호환성 확인.** Boot 4.2.0 BOM 하에서 springdoc-openapi, QueryDSL, spring-grpc의 호환 버전을 실제로 해석해 본다(`./gradlew dependencies`). 미호환이면 멈추고 사용자에게 보고한다. 확정 버전은 `gradle.properties`에 둔다.
- [ ] **Step 2b: Jib 호환성.** `com.google.cloud.tools.jib` 플러그인이 Gradle 버전·Java 21·Boot 4.2.0과 동작하는지 확인한다(`./gradlew :api-rest:jibDockerBuild --image=probe` 가능 여부, 이때는 임시 `bootJar` 대상). 안 되면 멈추고 사용자에게 보고한다.
- [ ] **Step 3: Green.** 멀티모듈 빌드와 `docker-compose.yml`(postgres:17, 포트 5432, 비밀번호는 `${POSTGRES_PASSWORD}`)을 작성한다. Checkstyle은 `MethodLength max=60`, `FileLength max=350`.
- [ ] **Step 4: Verify.** Run: `./gradlew check` → Expected: BUILD SUCCESSFUL, SmokeTest PASS.
- [ ] **Step 5: Commit** `chore: scaffold multi-module build with checkstyle and testcontainers`.

### Task 2: core 도메인과 JPA (Flyway)

**Files:** Create `core/.../common/{NotFoundException,ForbiddenException,DuplicateException}.java`, `core/.../member/{Member,Role,MemberRepository}.java`, `core/.../post/{Post,PostRepository}.java`, `db/migration/V1__init.sql`. Test: `MemberRepositoryTest`, `PostRepositoryTest`.

**Interfaces:**
- `enum Role { USER, ADMIN }`
- `Member`: `Long id`, `String email`(unique), `String passwordHash`, `String name`, `Role role`, `Instant createdAt`. 정적 팩토리 `Member.create(String email, String passwordHash, String name, Role role)`, `void rename(String name)`.
- `Post`: `Long id`, `Member member`(LAZY), `String title`, `String content`, `Instant createdAt`, `Instant updatedAt`. `Post.create(Member, String title, String content)`, `void edit(String title, String content)`.
- `MemberRepository extends JpaRepository<Member, Long>`: `Optional<Member> findByEmail(String)`, `boolean existsByEmail(String)`.
- `PostRepository extends JpaRepository<Post, Long>`.
- 예외 세 개는 `RuntimeException` 하위, 메시지 생성자 `(String message)`.

- [ ] **Step 1: Red.** `MemberRepositoryTest.findByEmail_returnsSavedMember`, `existsByEmail_falseForUnknown`, `PostRepositoryTest.save_persistsAuthorRelation`(저장 후 `post.getMember().getId()` 일치)를 `@DataJpaTest` + `PostgresTestBase`로 쓴다. Run: `./gradlew :core:test` → FAIL.
- [ ] **Step 2: Green.** 엔티티·리포지토리·`V1__init.sql`(members, posts, FK, `members.email` unique 인덱스)을 구현한다. `spring.jpa.hibernate.ddl-auto=validate`로 Flyway 스키마와 엔티티를 대조한다.
- [ ] **Step 3: Verify.** Run: `./gradlew :core:check` → PASS.
- [ ] **Step 4: Commit** `feat(core): add member and post entities with flyway schema`.

### Task 3: QueryDSL 동적 검색

**Files:** Create `core/.../post/{PostSearchCondition,PostQueryRepository,PostQueryRepositoryImpl}.java`, `core/.../config/QueryDslConfig.java`(`JPAQueryFactory` 빈). Modify `PostRepository`(extends `PostQueryRepository`). Test: `PostQueryRepositoryTest`.

**Interfaces:**
- `record PostSearchCondition(String keyword, Long memberId)` (둘 다 null 허용)
- `interface PostQueryRepository { Page<Post> search(PostSearchCondition cond, Pageable pageable); }`

- [ ] **Step 1: Red.** 테스트를 쓴다: `search_keywordMatchesTitleOrContent`, `search_memberIdFiltersAuthor`, `search_nullConditionReturnsAllPaged`(총 건수와 `getTotalElements` 단언), `search_percentInKeywordIsLiteral`(`%`가 와일드카드로 동작하지 않음), `search_blankKeywordIgnored`. Run → FAIL.
- [ ] **Step 2: Green.** `BooleanExpression` 조합으로 구현한다. 키워드는 `containsIgnoreCase`(QueryDSL이 이스케이프 처리), 빈 문자열은 조건 제외. 정렬은 `createdAt desc, id desc`. Count 쿼리는 분리.
- [ ] **Step 3: Verify.** `./gradlew :core:check` → PASS.
- [ ] **Step 4: Commit** `feat(core): add querydsl post search`.

### Task 4: 서비스 계층과 권한 규칙

**Files:** Create `core/.../member/{MemberService,MemberResult}.java`, `core/.../post/{PostService,PostResult}.java`, `core/.../security/AuthUser.java`. Test: `MemberServiceTest`, `PostServiceTest`(Mockito 단위 + 필요 시 통합).

**Interfaces:**
- `record AuthUser(Long id, Role role)`; `boolean isAdmin()`.
- `record MemberResult(Long id, String email, String name, Role role)`, `record PostResult(Long id, Long memberId, String title, String content, Instant createdAt)`.
- `MemberService`: `MemberResult register(String email, String rawPassword, String name)`(중복 → `DuplicateException`), `MemberResult get(AuthUser actor, Long id)`(**본인 또는 ADMIN만**, 그 외 `ForbiddenException`; 없으면 `NotFoundException`이 먼저 — 사용자 결정으로 변경됨), `Page<MemberResult> list(Pageable)`, `MemberResult update(AuthUser actor, Long id, String name)`, `void delete(AuthUser actor, Long id)`(본인 또는 ADMIN, 아니면 `ForbiddenException`; 없으면 `NotFoundException`).
- `PostService`: `PostResult create(AuthUser actor, String title, String content)`, `PostResult get(Long id)`, `Page<PostResult> search(PostSearchCondition cond, Pageable pageable)`(size 상한 100, 초과 시 100으로 보정), `PostResult update(AuthUser actor, Long postId, String title, String content)`, `void delete(AuthUser actor, Long postId)`(작성자 또는 ADMIN만).
- 비밀번호는 `PasswordEncoder`(BCrypt)로 해시. Result 레코드에 해시 필드 없음.

- [ ] **Step 1: Red.** 테스트: `register_duplicateEmailThrowsDuplicate`, `register_storesHashedPassword`(원문과 다르고 `matches` true), `post_updateByOtherUserThrowsForbidden`, `post_updateByAdminSucceeds`, `post_deleteMissingThrowsNotFound`, `search_sizeOver100ClampedTo100`. Run: `./gradlew :core:test` → FAIL.
- [ ] **Step 2: Green.** 서비스를 `@Transactional`(조회는 `readOnly`)로 구현한다. 권한 검사는 서비스 내부 private 메서드 한 곳으로 모은다.
- [ ] **Step 3: Verify.** `./gradlew :core:check` → PASS.
- [ ] **Step 4: Commit** `feat(core): add member and post services with ownership rules`.

### Task 5: JWT 발급·검증과 인증 서비스

**Files:** Create `core/.../security/{JwtProperties,JwtProvider,TokenPair,AuthService,InvalidTokenException,CurrentUser,JwtConfig}.java`. Test: `JwtProviderTest`, `AuthServiceTest`.

**Interfaces:**
- `@ConfigurationProperties("app.jwt") record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl)`. 시크릿은 `${JWT_SECRET}`로 주입하고 32바이트 미만이면 기동 실패.
- `JwtConfig`: HS256 `JwtEncoder`, `JwtDecoder` 빈, `PasswordEncoder`(BCrypt) 빈.
- `JwtProvider`: `String issueAccess(Member)`, `String issueRefresh(Member)`, `AuthUser parseAccess(String token)`(`InvalidTokenException`), `AuthUser parseRefresh(String token)`. 클레임: `sub`=memberId, `role`, `typ`=`access`|`refresh`.
- `record TokenPair(String accessToken, String refreshToken)`.
- `AuthService`: `TokenPair login(String email, String rawPassword)`(이메일 없음과 비밀번호 불일치 모두 같은 메시지의 `BadCredentialsException`), `TokenPair refresh(String refreshToken)`.
- `CurrentUser.get(): AuthUser` — `SecurityContextHolder`의 `Jwt`에서 `AuthUser` 생성(REST·GraphQL 공용).

- [ ] **Step 1: Red.** 테스트: `parseAccess_roundTripsIdAndRole`, `parseAccess_expiredTokenThrows`, `parseAccess_tamperedSignatureThrows`, `parseAccess_rejectsRefreshToken`, `parseRefresh_rejectsAccessToken`, `login_wrongPasswordAndUnknownEmailThrowSameException`, `refresh_returnsNewPair`, `jwtProperties_shortSecretFailsStartup`. Run → FAIL.
- [ ] **Step 2: Green.** Nimbus 기반 `NimbusJwtEncoder/Decoder`를 쓰고 `typ` 클레임을 검증한다. 만료 테스트는 `Clock` 주입 또는 짧은 TTL로 결정적으로 작성한다.
- [ ] **Step 3: Verify.** `./gradlew :core:check` → PASS.
- [ ] **Step 4: Commit** `feat(core): add self-issued jwt and auth service`.

### Task 6: api-rest (REST + Security + Swagger)

**Files:** Create `api-rest/.../rest/RestApplication.java`, `config/SecurityConfig.java`, `config/OpenApiConfig.java`, `auth/AuthController.java`, `member/MemberController.java`, `post/PostController.java`, `error/RestExceptionHandler.java`, `resources/application.yml`, `README.md`. Test: `AuthControllerTest`, `PostControllerTest`, `RestSecurityIntegrationTest`.

**Interfaces:**
- `POST /auth/login`(`{email,password}`→`TokenPair`), `POST /auth/refresh`, `POST /api/members`(가입, 인증 불필요), `GET/PUT/DELETE /api/members/{id}`(조회·수정·삭제 모두 본인 또는 ADMIN만; 다른 회원 이메일이 노출되지 않도록), `GET /api/posts?keyword&memberId&page&size`, `POST /api/posts`, `GET/PUT/DELETE /api/posts/{id}`. 목록 응답은 `PageResponse<T>(List<T> items, int page, int size, long total)`.
- `SecurityConfig`: stateless, CSRF 비활성(토큰 기반 근거를 README에 기록), `oauth2ResourceServer(jwt)` + `role` 클레임을 `ROLE_*` 권한으로 변환, `/auth/**`·`POST /api/members`·`/v3/api-docs/**`·`/swagger-ui/**`만 permitAll.
- `RestExceptionHandler`: `ProblemDetail`로 `NotFound`→404, `Forbidden`→403, `Duplicate`→409, 검증 실패→400, `BadCredentials`→401. 내부 예외 메시지·스택은 노출하지 않는다.
- `OpenApiConfig`: Bearer JWT 보안 스킴 등록.

- [ ] **Step 1: Red.** 테스트(`@WebMvcTest` + 서비스 목, 보안 통합은 Testcontainers): `login_returnsTokens`, `listPosts_withoutTokenReturns401`, `listPosts_expiredTokenReturns401`, `createPost_blankTitleReturns400`, `updatePost_byOtherUserReturns403`, `register_duplicateEmailReturns409`, `listPosts_sizeOver100Clamped`, `listPosts_negativePageReturns400`, `swaggerUi_accessibleWithoutToken`, `apiDocs_declaresBearerScheme`, 그리고 전체 흐름 `registerLoginCreateSearchDelete`. Run: `./gradlew :api-rest:test` → FAIL.
- [ ] **Step 2: Green.** 컨트롤러는 요청 검증(`@Valid`) 후 서비스만 호출한다. 요청 DTO는 `record` + Bean Validation.
- [ ] **Step 3: Verify.** `./gradlew :api-rest:check` → PASS. 수동: `docker compose up -d`, `JWT_SECRET=... ./gradlew :api-rest:bootRun`, `/swagger-ui.html` 접속.
- [ ] **Step 4: Commit** `feat(api-rest): add rest crud, jwt security and openapi`.

### Task 7: api-graphql

**Files:** Create `api-graphql/.../graphql/GraphqlApplication.java`, `config/SecurityConfig.java`, `member/MemberGraphqlController.java`, `post/PostGraphqlController.java`, `resources/graphql/schema.graphqls`, `application.yml`, `README.md`. Test: `PostGraphqlTest`, `GraphqlSecurityIntegrationTest`.

**Interfaces:**
- 스키마: `type Member`, `type Post`, `type PostPage { items: [Post!]! page: Int! size: Int! total: Int! }`, `Query { post(id: ID!): Post, posts(keyword: String, memberId: ID, page: Int = 0, size: Int = 20): PostPage!, member(id: ID!): Member }`, `Mutation { register, login, createPost, updatePost, deletePost }`.
- `/graphql`은 인증 필요(`login`·`register` 뮤테이션만 예외 처리: 익명 접근 가능하도록 SecurityConfig에서 경로가 아니라 메서드 수준으로 구분하거나 별도 인증 엔드포인트를 둔다 — 구현자 선택, README에 근거 기록). `CurrentUser.get()` 사용.
- 예외는 GraphQL 에러의 `extensions.code`(`NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `UNAUTHENTICATED`)로 매핑하는 `DataFetcherExceptionResolver`.

- [ ] **Step 1: Red.** 테스트(`GraphQlTester`): `posts_returnsPagedItems`, `posts_sizeOver100Clamped`, `createPost_withoutTokenIsUnauthenticated`, `updatePost_byOtherUserHasForbiddenCode`, `register_duplicateEmailHasConflictCode`, `post_missingIdHasNotFoundCode`. Run: `./gradlew :api-graphql:test` → FAIL.
- [ ] **Step 2: Green.** `@QueryMapping`/`@MutationMapping`/`@Argument`로 구현한다. N+1은 `@BatchMapping`으로 `Post.member`를 해소한다(쿼리 수 단언 테스트 `posts_loadsAuthorsWithoutNPlusOne` 추가).
- [ ] **Step 3: Verify.** `./gradlew :api-graphql:check` → PASS.
- [ ] **Step 4: Commit** `feat(api-graphql): add graphql crud with jwt security`.

### Task 8: api-grpc

**Files:** Create `api-grpc/src/main/proto/{auth,member,post}.proto`, `.../grpc/GrpcApplication.java`, `auth/AuthGrpcService.java`, `member/MemberGrpcService.java`, `post/PostGrpcService.java`, `config/{JwtServerInterceptor,GrpcExceptionMapper}.java`, `application.yml`, `README.md`. Test: `PostGrpcServiceTest`, `JwtServerInterceptorTest`.

**Interfaces:**
- Proto: `service AuthService { Login, Refresh }`, `service MemberService { Register, Get, Update, Delete }`, `service PostService { Create, Get, Search, Update, Delete }`. `SearchPostsRequest{ string keyword = 1; int64 member_id = 2; int32 page = 3; int32 size = 4; }`.
- `JwtServerInterceptor`: 메타데이터 `authorization: Bearer <access>`를 `JwtProvider.parseAccess`로 검증, `AuthUser`를 gRPC `Context`에 저장. `Login`/`Refresh`/`Register`는 인증 예외 목록. 검증 실패 → `Status.UNAUTHENTICATED`.
- `GrpcExceptionMapper`: `NotFound`→`NOT_FOUND`, `Forbidden`→`PERMISSION_DENIED`, `Duplicate`→`ALREADY_EXISTS`, 검증 오류→`INVALID_ARGUMENT`.

- [ ] **Step 1: Red.** in-process 서버 테스트: `createPost_withoutMetadataIsUnauthenticated`, `createPost_expiredTokenIsUnauthenticated`, `updatePost_byOtherUserIsPermissionDenied`, `register_duplicateIsAlreadyExists`, `search_returnsPage`, `search_sizeOver100Clamped`, `registerLoginCreateGetDelete`(전체 흐름). Run: `./gradlew :api-grpc:test` → FAIL.
- [ ] **Step 2: Green.** 서비스는 서비스 계층 호출 + proto 변환만. 변환 로직은 `Mapper` 클래스로 분리(350줄 가이드).
- [ ] **Step 3: Verify.** `./gradlew :api-grpc:check` → PASS. 수동: `grpcurl`로 로그인 후 `-H "authorization: Bearer ..."` 호출.
- [ ] **Step 4: Commit** `feat(api-grpc): add grpc crud with jwt interceptor`.

### Task 9: 문서화와 최종 검증

**Files:** Create/Modify 루트 `README.md`, 각 모듈 `README.md` 보강, `CLAUDE.md`(`claude-md-management:revise-claude-md` 사용). Test: 없음(문서). 전체 검증 명령 실행.

- [ ] **Step 1:** 루트 README에 모듈 지도, AI-DLC 가이드 링크(`docs/ai-dlc/GUIDE.md`), 모니터링(Prometheus 실행·메트릭 목록), AOP 로그 읽는 법, 실행 순서(`cp .env.example .env` → `JWT_SECRET` 입력 → `docker compose up -d` → `bootRun`), 세 프로토콜 호출 예제(curl, GraphQL 쿼리, grpcurl), TDD 워크플로 요약, 모듈을 가져가는 법을 쓴다.
- [ ] **Step 2: Verify.** Run: `./gradlew clean check` → Expected: BUILD SUCCESSFUL(전 모듈 테스트, Checkstyle 위반 0).
- [ ] **Step 3: 시크릿 점검.** Run: `grep -rniE "(password|secret|apikey)\s*[:=]\s*[\"'][^$]" --include=*.java --include=*.yml --include=*.gradle . | grep -v src/test` → Expected: 출력 없음.
- [ ] **Step 3b: Jib 이미지.** 세 api 모듈과 `batch` 모듈에 Jib를 설정한다(`batch`의 설정은 Task 15에서 이미 되어 있으면 확인만)(`from.image=eclipse-temurin:21-jre`, `to.image=benggri/spring-boot-example-<module>:${version}`, 컨테이너 포트 노출, `environment`에는 시크릿 넣지 않음). 공통 설정은 루트 `build.gradle`의 `subprojects` 블록에서 `api-*`에만 적용한다. Run: `./gradlew :api-rest:jibDockerBuild` 후 `docker run --rm --add-host=host.docker.internal:host-gateway -e JWT_SECRET=<32바이트 이상 임의값> -e SPRING_DATASOURCE_URL=... <image>`로 기동 확인, `docker history <image>`와 `docker inspect`에서 시크릿 문자열이 없는지 확인한다. Expected: 네 이미지(api-rest, api-graphql, api-grpc, batch) 모두 빌드·기동 성공(batch는 `runDate` 인자로 잡 1회 실행 후 종료), 이미지에 시크릿 없음. 루트 README에 이미지 빌드·실행법을 추가한다.
- [ ] **Step 4:** `/claude-md-management:revise-claude-md`로 `CLAUDE.md`를 작성·개선한다(빌드·테스트 명령, 모듈 구조, 가이드).
- [ ] **Step 5: Commit** `docs: add readmes and claude.md`.

### Task 10: Spring AOP (로깅·감사) — **Task 5 직후, Task 6 전에 실행**

**Files:** Create `core/.../aop/{AopProperties,Audited,ServiceLoggingAspect,AuditAspect}.java`. Modify `core/build.gradle`(AOP 스타터; Boot 4에서의 정확한 아티팩트명은 해석해 확인), `MemberService`·`PostService`의 쓰기 메서드에 `@Audited` 부착. Test: `ServiceLoggingAspectTest`, `AuditAspectTest`, `AopIntegrationTest`.

**Interfaces:**
- `@ConfigurationProperties("app.aop") record AopProperties(long slowThresholdMs)` (기본 500).
- `@Retention(RUNTIME) @Target(METHOD) @interface Audited { String value(); }` (동작명 예: `post.delete`).
- `ServiceLoggingAspect`: `@Around("execution(* com.benggri.example.core..*Service.*(..))")` — DEBUG로 `class.method took Nms`, `slowThresholdMs` 초과 시 WARN. 인자 값은 절대 기록하지 않는다(메서드명만).
- `AuditAspect`: `@Around("@annotation(audited)")` — 인자 중 `AuthUser`가 있으면 그 `id`, 없으면 `anonymous`를 행위자로, `action=<value> actor=<id> outcome=SUCCESS|FAILURE(<예외 클래스명>)`을 INFO로 기록. 예외는 그대로 다시 던진다.
- 로거 이름: `audit` (감사 로그 분리용).

- [ ] **Step 1: Red.** Logback `ListAppender`로 테스트: `logging_recordsMethodAndDuration`, `logging_warnsWhenOverThreshold`(임계값 0으로 설정), `logging_neverContainsArgumentValues`(`register("a@b.c", "s3cretPw!", "n")` 호출 후 로그에 `s3cretPw!`·`a@b.c` 없음), `audit_logsActorAndSuccess`, `audit_logsFailureAndRethrowsSameException`(같은 인스턴스), `audit_usesAnonymousWithoutAuthUser`, `aspects_doNotChangeReturnValue`, 통합 `aop_appliesToTransactionalServiceProxies`(실제 `PostService.create` 호출 시 audit 로그 1건). Run: `./gradlew :core:test` → FAIL.
- [ ] **Step 2: Green.** 위 인터페이스대로 구현. `@Audited`는 `MemberService.register/update/delete`, `PostService.create/update/delete`에 붙인다.
- [ ] **Step 3: Verify.** `./gradlew :core:check` → PASS.
- [ ] **Step 4: Commit** `feat(core): add logging and audit aspects`.

### Task 11: Prometheus 모니터링 — **Task 8 직후, Task 9 전에 실행**

**Files:** Create `monitoring/prometheus.yml`, `api-rest|api-graphql|api-grpc/.../config/ManagementSecurityConfig.java`(rest·graphql; grpc는 아래 참고). Modify 각 `api-*/build.gradle`(actuator, `micrometer-registry-prometheus`), 각 `application.yml`, `docker-compose.yml`(프로필 `monitoring`의 prometheus 서비스), `core/.../aop/ServiceLoggingAspect.java`(Timer 추가), `ServiceLoggingAspectTest`. Test: 각 모듈 `ManagementEndpointsTest`.

**Interfaces:**
- 각 앱 `application.yml`: `management.server.port`(rest 8081, graphql 8082, grpc 8083), `management.endpoints.web.exposure.include=health,prometheus`, `management.endpoint.health.probes.enabled=true`.
- `ManagementSecurityConfig`: `SecurityFilterChain`(`@Order(1)`, `EndpointRequest.toAnyEndpoint()`에 대해 `health`·`prometheus`만 permitAll, 나머지 denyAll).
- `ServiceLoggingAspect`: 호출마다 `Timer`를 `app.service.invocation`(태그 `class`, `method`, `outcome`=`success|failure`)로 기록. `MeterRegistry`는 생성자 주입.
- `api-grpc`: gRPC 포트와 별개로 actuator용 서블릿 컨테이너를 management 포트에만 띄운다. spring-grpc가 제공하는 gRPC 서버 관측(observation) 인터셉터가 있으면 켠다. 구현 방식은 구현자가 정하고 README에 근거를 기록한다.
- `monitoring/prometheus.yml`: 세 앱의 management 포트를 `host.docker.internal`로 scrape(`/actuator/prometheus`).

- [ ] **Step 1: Red.** 테스트(`@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@LocalManagementPort`, Testcontainers): `prometheus_returnsMetricsOnManagementPort`(200, 본문에 `jvm_memory_used_bytes`), `prometheus_notExposedOnMainPort`(404 또는 401), `env_endpointNotExposed`(management 포트에서 404), `health_isUp`, `serviceCall_recordsInvocationTimer`(서비스 호출 후 `app_service_invocation_seconds_count`가 1 이상, 라벨에 `outcome="success"`), `serviceFailure_recordsFailureOutcome`, `metrics_neverContainPasswordOrEmail`. 코어 단위: `ServiceLoggingAspectTest.recordsTimerWithClassMethodOutcomeTags`(`SimpleMeterRegistry`). Run: `./gradlew check` → FAIL.
- [ ] **Step 2: Green.** 위 인터페이스대로 구현한다. docker-compose의 prometheus는 `profiles: ["monitoring"]`와 `extra_hosts: ["host.docker.internal:host-gateway"]`(Linux/WSL 엔진에서 호스트 앱 scrape용), `monitoring/prometheus.yml`을 볼륨 마운트하고 시크릿을 넣지 않는다.
- [ ] **Step 3: Verify.** Run: `./gradlew check` → PASS. 수동: `docker compose --profile monitoring up -d` 후 Prometheus UI(`:9090`) targets가 UP인지 확인(앱 기동 상태에서).
- [ ] **Step 4: Commit** `feat: expose prometheus metrics on management port`.

### Task 12: AI-DLC 가이드 문서 — **Task 11 직후, Task 9 전에 실행**

**Files:** Create `docs/ai-dlc/GUIDE.md`. Test: 없음(문서). 경로 검증 명령으로 대체.

**Interfaces:** Produces: 루트 README(Task 9)가 링크할 `docs/ai-dlc/GUIDE.md`.

가이드에 반드시 들어갈 절(8개, 한국어, 170줄 안팎, 한 번에 쓰지 말고 절 단위로 작성):
1. **개요:** AI-DLC가 무엇이고 이 저장소가 왜 Spec-driven + Subagent-driven을 쓰는지. AWS AI-DLC 원문과 구분해 "이 저장소의 해석"임을 명시.
2. **단계 대응표:** Intent=`INITIAL-REQUIREMENTS.md`, Inception=브레인스토밍→spec(`docs/superpowers/specs/`)→계획(`docs/superpowers/plans/`), Unit=계획의 Task, Bolt=Task 하나의 구현→리뷰→수정 사이클, Construction=subagent-driven-development, Operations=Jib 이미지·Prometheus·README 런북·CLAUDE.md.
3. **Spec-driven 규칙:** spec이 최종 권위, 계획은 spec의 논거. 요구사항 변경은 문서(요구사항→spec→계획)를 먼저 고친 뒤 구현. 이 프로젝트에서 실제로 일어난 변경(PostgreSQL, TDD, Jib, AOP, Prometheus) 예시를 표로 제시.
4. **Subagent-driven 규칙:** Task마다 새 구현 서브에이전트(brief 파일 전달), Task 리뷰(스펙 준수+품질), fix loop, 최종 전체 리뷰, ledger(`.superpowers/sdd/<plan>/progress.md`)로 진행 기록, 모델은 작업 난이도별 선택, 판단 사항은 `Ruling:`으로 기록.
5. **사람의 게이트:** 요구사항 확정, spec 승인, 계획 검토, 최종 리뷰·병합. 에이전트가 멈추고 묻는 경우(파괴적 작업, 보안 민감 작업, 외부 부작용, 계획 붕괴).
6. **품질 가드레일:** TDD(Red→Green→Refactor, Green에서만 커밋), 함수 60줄/클래스 350줄(Checkstyle), 시크릿 하드코딩 금지, 인자 값 로깅 금지.
7. **따라 하기:** 새 기능을 추가할 때의 단계별 체크리스트(요구사항 수정 → `/superpowers:brainstorming` → spec 승인 → `writing-plans` → `subagent-driven-development` → `claude-md-management`로 CLAUDE.md 갱신).
8. **OKF(Open Knowledge Format)와 graphify 비교·활용 (선택):** (a) 무엇인가: Google Cloud가 공개한, 마크다운 파일 + YAML frontmatter로 AI 에이전트용 지식을 담는 번들 형식. 개념 하나가 파일 하나, 필수 필드는 `type`뿐, `index.md`(점진적 공개)·`log.md`는 선택, `type` 없는 `.md`(README.md, AGENTS.md, CLAUDE.md 등)는 건너뜀. (b) 상태: 2026-11 기준 v0.3, 1.0 이전이라 minor에서도 깨지는 변경 가능 — 도입 전 공식 저장소(`github.com/open-knowledge-format/open-knowledge-format`)에서 최신 버전 확인. (c) 에이전트 스킬: `okf-generate`(번들 작성·갱신·마이그레이션·검증), `okf-consume`(번들 기반 질의응답, 신뢰 등급·상태·최신성 보고); Agent Skills 형식이며 설치법은 저장소 README `#agent-skills`. (d) 이 프로젝트에서의 위치: 필수 아님. 기존 문서(CLAUDE.md, spec, 계획)는 OKF가 아니며 바꾸지 않는다. 도메인 개념·결정 기록처럼 여러 서브에이전트가 반복해 참조할 지식이 커질 때 `docs/knowledge/` 번들로 분리하는 선택지로만 소개하고, 예시 레이아웃(`docs/knowledge/index.md`, `domain/member.md`, `decisions/jwt-self-issued.md`)과 frontmatter 예(`type` 값은 공식 예시·스펙을 확인해 그대로 쓰고 임의로 만들지 않는다)를 짧게 보여 준다. (e) 한계: 컨테이너(폴더·frontmatter) 규격이지 지식의 의미까지 표준화하지 않는다는 비판이 있음. (f) graphify와의 비교(표로): graphify는 이 환경에 설치된 스킬로, 코드·문서·논문·이미지·영상을 지식 그래프(대화형 HTML, GraphRAG용 JSON, `GRAPH_REPORT.md`)로 **자동 생성**하고 `/graphify query|path|explain`으로 질의하며, `--update`(증분), `--watch`, `--mcp`(에이전트 접근), `--wiki`, `--obsidian` 옵션과 엣지 신뢰 표기(EXTRACTED/INFERRED/AMBIGUOUS)를 제공한다. OKF는 **사람·에이전트가 작성하는** 지식 파일의 형식이다. 대체 관계가 아니라 보완 관계: 코드 구조·관계 탐색은 graphify, 도메인 용어·설계 결정처럼 선별한 지식의 저장·공유는 OKF. 이 프로젝트 권고: 코드가 적은 초기에는 둘 다 불필요, 모듈이 쌓인 뒤(구현 후)에는 graphify가 더 유리(서비스→어댑터→보안 관계 질의, 서브에이전트 컨텍스트 절감, 별도 문서 유지 불필요). 선택 사항으로 구현 후 `/graphify`를 한 번 실행해 `graphify-out/`을 만드는 방법을 안내하되, 필수 단계로 만들지 않는다. graphify의 추출 정확도·갱신 비용은 직접 검증하지 않았음을 명시한다. 사실이 아닌 필드·명령은 쓰지 않는다.

- [ ] **Step 1: 작성.** 위 8개 절을 쓴다(OKF 절은 마지막)(절마다 Write/Edit로 나눠서). 사실이 아닌 내용(없는 명령·경로)을 쓰지 않는다.
- [ ] **Step 2: Verify.** Run: `grep -o '`docs/[^`]*`' docs/ai-dlc/GUIDE.md | tr -d '`' | sort -u | xargs -I{} sh -c 'test -e "{}" || echo MISSING {}'` → Expected: 출력 없음(참조한 docs 경로가 모두 존재). 8개 절 제목이 모두 있는지 `grep -c '^## ' docs/ai-dlc/GUIDE.md` → 8 이상.
- [ ] **Step 3: Commit** `docs: add ai-dlc guide`.

### Task 13: Lombok 적용 — **Task 4 직후, Task 5 전에 실행**

**Files:** Modify `build.gradle`(공통) 또는 `core/build.gradle`(Lombok `compileOnly` + `annotationProcessor`, 테스트용 `testCompileOnly`/`testAnnotationProcessor`), 필요 시 `lombok.config`, `core/.../member/Member.java`, `core/.../post/Post.java` 및 이 시점까지 수작업 getter/setter가 있는 모든 main 클래스. Test: 기존 테스트 전체(안전망) + `LombokAccessorsTest`.

**Interfaces:** Produces: 이후 모든 Task가 getter/setter를 Lombok으로만 만든다. 공개 API(메서드 이름·시그니처, 예: `Member.getEmail()`, `Post.getMember()`)는 변하지 않는다.

- [ ] **Step 1: Baseline.** Run: `./gradlew check` → Expected: PASS(변경 전 기준선).
- [ ] **Step 2: Red.** `LombokAccessorsTest`(core 테스트)를 쓴다: `member_gettersAreGeneratedByLombok`(`lombok.config`에 `lombok.addLombokGeneratedAnnotation = true`를 두고, 리플렉션으로 `Member`의 `getEmail`, `getRole`, `getName` 메서드에 `lombok.Generated` 어노테이션이 붙어 있음을 단언; 어노테이션이 CLASS 보존이면 `javap`/바이트코드 대신 `getDeclaredMethod(...).getAnnotations()`가 비어 보일 수 있으므로, 이 경우 `@lombok.Generated`의 retention을 확인해 가능한 방법(예: `lombok.addLombokGeneratedAnnotation` + 클래스 파일 읽기)을 구현자가 고른다), `post_gettersAreGeneratedByLombok`(`getTitle`, `getContent`, `getMember`), `entities_haveNoPublicSetters`(Member, Post의 public `set*` 메서드 0개). Run: `./gradlew :core:test --tests '*LombokAccessorsTest'` → Expected: FAIL(수작업 getter에 Generated 표시 없음).
- [ ] **Step 3: Green.** Lombok 의존성을 추가한다. **QueryDSL apt와의 처리 순서를 지킨다(`annotationProcessor` 목록에서 Lombok을 querydsl-apt보다 먼저).** `Member`, `Post`의 수작업 getter를 지우고 클래스에 `@Getter`를 붙인다. `@Setter`는 붙이지 않는다. 다른 main 클래스의 수작업 getter/setter도 같은 방식으로 바꾼다(record는 건드리지 않음).
- [ ] **Step 4: Verify.** Run: `./gradlew check` → Expected: PASS(기존 테스트 전체와 Q클래스 생성 포함, Checkstyle 위반 0). Run: `grep -rnE "public [A-Za-z<>?, \[\]]+ (get|set)[A-Z][A-Za-z]*\(" --include=*.java */src/main` → Expected: 수작업 getter/setter 없음(출력 없음; 도메인 메서드 `isAdmin()` 등은 `get`/`set` 접두사가 아니므로 해당 없음).
- [ ] **Step 5: Commit** `refactor: replace handwritten accessors with lombok`.

### Task 14: 패키지 구조 정리 — **Task 4 직후, Task 13 전에 실행**

**Files:** Move(`git mv`) + package/import 수정: `core/.../member/{Member,Role}.java`→`member/entity/`, `MemberRepository`→`member/repository/`, `MemberService`→`member/service/`, `MemberResult`→`member/dto/`; `post/Post`→`post/entity/`, `PostRepository`·`PostQueryRepository`·`PostQueryRepositoryImpl`→`post/repository/`, `PostService`→`post/service/`, `PostResult`·`PostSearchCondition`→`post/dto/`. 테스트도 같은 패키지로 이동. `QueryDslConfig`는 `core/config`, `AuthUser`는 `core/security`, 예외 3종은 `core/common`에 그대로. Test: `PackageStructureTest`(신규) + 기존 테스트 전체.

**Interfaces:** Produces: 이후 Task가 쓰는 정확한 FQN — `com.benggri.example.core.member.entity.{Member,Role}`, `...member.repository.MemberRepository`, `...member.service.MemberService`, `...member.dto.MemberResult`, `...post.entity.Post`(Q클래스 `QPost`도 `post.entity`), `...post.repository.{PostRepository,PostQueryRepository,PostQueryRepositoryImpl}`, `...post.service.PostService`, `...post.dto.{PostResult,PostSearchCondition}`. 메서드 시그니처는 변하지 않는다. 패키지가 달라져 접근이 막히는 `MemberResult.from`·`PostResult.from`은 `public`으로 바꾼다.

- [ ] **Step 1: Baseline.** Run: `./gradlew check` → PASS.
- [ ] **Step 2: Red.** `PackageStructureTest`를 쓴다: 위 FQN 11개 각각에 `Class.forName(...)`이 성공해야 하고(`structure_hasExpectedClasses`), `core/src/main/java/.../member`와 `.../post` 직하위에는 `.java` 파일이 없어야 한다(`structure_featureFoldersContainOnlyLayerFolders`, `Files.list`로 확인, 테스트 작업 디렉터리 기준 경로는 `Path.of("src/main/java/com/benggri/example/core/post")`). Run: `./gradlew :core:test --tests '*PackageStructureTest'` → Expected: FAIL(`ClassNotFoundException`).
- [ ] **Step 3: Green.** `git mv`로 이동하고 `package` 선언·`import`·Spring Data 스캔·`QueryDslConfig`의 참조를 고친다. `@Entity` 스캔과 Flyway/JPA 설정이 이동 후에도 동작해야 한다(`ddl-auto=validate` 통과).
- [ ] **Step 4: Verify.** Run: `./gradlew check` → PASS(Q클래스 생성, Checkstyle 위반 0). Run: `find core/src/main/java/com/benggri/example/core/member core/src/main/java/com/benggri/example/core/post -maxdepth 1 -name '*.java'` → Expected: 출력 없음.
- [ ] **Step 5: Commit** `refactor(core): organize packages by feature and layer`.

### Task 15: Spring Batch 예제 모듈 — **Task 11 직후, Task 12 전에 실행**

**Files:** Create `batch/build.gradle`, `batch/src/main/java/com/benggri/example/batch/BatchApplication.java`, `.../batch/config/{PersistenceConfig,BatchProperties}.java`, `.../batch/job/MemberPostStatsJobConfig.java`, `.../batch/step/{PostCountReader 설정, PostCountProcessor, StatWriter 설정, StatsPurgeTasklet}`(구현 형태는 구현자 선택, 각 클래스 단일 책임), `.../batch/dto/PostCountRow.java`, `batch/src/main/resources/application.yml`, `batch/README.md`; Create `core/src/main/resources/db/migration/V2__member_post_stats.sql`, `core/.../stats/entity/MemberPostStat.java`(+`repository`는 필요할 때만). Modify `settings.gradle`(`include 'batch'`), 루트 `build.gradle`(`api-*` 조건에 `batch`를 포함해 Jib 적용과 core 의존 연결). Test: `MemberPostStatsJobTest`, `PostCountProcessorTest`, `StatsPurgeTaskletTest`, `RunDateValidatorTest`.

**Interfaces:**
- 잡 이름 `memberPostStatsJob`, 파라미터 `runDate`(ISO `yyyy-MM-dd`, 필수, 형식 오류 시 시작 거부 — `JobParametersValidator`).
- `V2__member_post_stats.sql`: `member_post_stats(member_id BIGINT NOT NULL REFERENCES members(id), stat_date DATE NOT NULL, post_count INTEGER NOT NULL, created_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (member_id, stat_date))`. 회원 삭제 시 통계도 지워져야 하므로 **`ON DELETE CASCADE`를 우선 사용**한다(V1은 수정 금지; 다른 방식을 택하면 근거를 README에 기록). `core`에 테스트 `MemberService.delete`가 통계 행이 있는 회원에서도 성공함(`delete_memberWithStats_succeeds`)을 추가해, 새 FK가 세 API의 회원 삭제 500을 되살리지 않음을 보장한다.
- Step1 `aggregatePostCountsStep`(chunk, 크기 `app.batch.chunk-size` 기본 100): reader는 `runDate` 하루(시스템 기본 타임존이 아닌 UTC 경계, `created_at >= start AND < start+1d`)에 작성된 게시글을 `member_id`별로 `count`해 읽고(`PostCountRow(Long memberId, long postCount)`), processor가 행을 통계 행으로 변환하며 `postCount == 0`이면 `null`을 반환해 건너뛰고, writer가 `INSERT ... ON CONFLICT (member_id, stat_date) DO UPDATE`로 upsert한다(재실행 멱등). SQL은 파라미터 바인딩만.
- Step2 `purgeOldStatsStep`(tasklet): `stat_date < runDate - retentionDays` 행 삭제. `app.batch.retention-days`(기본 90, 1 이상).
- 설정: `spring.main.web-application-type: none`, `spring.batch.job.enabled`는 CLI로 잡을 지정해 실행하도록 구성(`--spring.batch.job.name=memberPostStatsJob runDate=2026-11-25`). DB 접속 정보와 시크릿은 환경 변수(`POSTGRES_PASSWORD` 기본값 없음, `DB_URL`/`DB_USERNAME`), Batch 메타데이터 스키마 초기화 방식은 구현자가 PostgreSQL + Flyway 조합에서 검증해 선택(`spring.batch.jdbc.initialize-schema`)하고 README에 기록. `@EntityScan`/`@EnableJpaRepositories`/`scanBasePackages`는 api-rest와 같은 패턴(`config/PersistenceConfig`).
- Spring Batch 6(Boot 4.2) API: `spring-boot-starter-batch`와 테스트 `spring-batch-test`의 정확한 아티팩트·패키지를 해석해 확인한다(`@EnableBatchProcessing` 필요 여부 포함).

- [ ] **Step 1: Red.** 테스트 설정에서 `spring.batch.job.enabled=false`로 컨텍스트 시작 시 잡이 자동 실행되지 않게 한다(자동 실행되면 `runDate` 검증 실패로 컨텍스트가 뜨지 않음). 테스트: `runDateValidator_rejectsMissingAndMalformedRunDate`, `processor_skipsZeroCountAndMapsOthers`, `job_aggregatesPostsOfRunDateOnly`(runDate에 작성된 게시글 3건(회원 A 2건, B 1건)과 전날·다음날 게시글이 있을 때 통계 2행: A=2, B=1), `job_rerunForSameRunDateIsIdempotent`(같은 `runDate`에 새 `JobParameters`(예: 고유 `run.id`)로 두 번 실행해도 통계 행 수와 값이 변하지 않음, 중간에 게시글이 늘면 값이 갱신됨), `job_purgesStatsOlderThanRetention`(경계: 보존 기간 정확히 경계일은 유지, 그 이전은 삭제), `job_failsOnMalformedRunDateWithoutWritingStats`, `job_deletingMemberDoesNotBreakStats`(회원 삭제 후에도 FK 위반 없이 동작/통계 정리). Testcontainers PostgreSQL(`PostgresTestBase`)로 실제 DB, `JobLauncherTestUtils`/`JobOperatorTestUtils`(Batch 6 API 확인), 행 정리는 `@AfterEach`. Run: `./gradlew :batch:test` → Expected: FAIL.
- [ ] **Step 2: Green.** 위 인터페이스대로 구현한다. 각 메서드 60줄 이하, 클래스 350줄 이하, getter/setter는 Lombok(`@Getter`) 또는 record.
- [ ] **Step 3: Verify.** `./gradlew :batch:check` → PASS, `./gradlew :batch:bootJar` 성공, `./gradlew :batch:tasks --all`에 `jibDockerBuild` 표시(이미지 빌드는 Task 9). 루트 `./gradlew check` PASS.
- [ ] **Step 4: Commit** `feat(batch): add member post stats batch job`.
