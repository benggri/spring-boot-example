# Spring Boot 예제 프로젝트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 모든 구현은 `superpowers:test-driven-development`를 따른다.

**Goal:** REST·GraphQL·gRPC가 같은 도메인과 서비스 계층을 공유하는, 학습·템플릿·가이드용 Spring Boot 멀티모듈 예제를 만든다.

**Architecture:** `core`(엔티티, JPA, QueryDSL, 서비스, JWT)에 `api-rest`, `api-graphql`, `api-grpc` 세 앱이 단방향으로 의존한다. 어댑터는 서비스만 호출하는 얇은 계층이다. JWT는 자체 발급하며 같은 토큰을 HTTP 헤더와 gRPC 메타데이터에 쓴다.

**Tech Stack:** Spring Boot 4.2.0, Java 21, Gradle Groovy, Spring Security(oauth2-jose), Spring Data JPA, QueryDSL, Flyway, PostgreSQL, springdoc-openapi, spring-graphql, spring-grpc, Testcontainers, Checkstyle, Jib.

**Spec:** `docs/superpowers/specs/2026-11-25-spring-boot-example-design.md`
**Requirements:** `docs/requirements/2026-11-25/01/INITIAL-REQUIREMENTS.md`

## Global Constraints

- Spring Boot 4.2.0, Java 21, Gradle Groovy DSL (`build.gradle`, `settings.gradle`).
- 모듈: `core`, `api-rest`, `api-graphql`, `api-grpc`. 의존은 `api-*` → `core` 단방향.
- 패키지 루트 `com.benggri.example` (`.core`, `.rest`, `.graphql`, `.grpc`). 도메인은 `Member` 1:N `Post`.
- 함수 60라인 이하, 클래스 350라인 이하, 한 함수 한 역할. Checkstyle(`MethodLength` 60, `FileLength` 350)로 강제한다.
- 인증은 자체 발급 JWT(access/refresh), 역할 `USER`/`ADMIN`. 시크릿은 환경 변수 `JWT_SECRET`, 코드·yml에 기본값 하드코딩 금지.
- DB는 PostgreSQL. 실행은 docker-compose, 테스트는 Testcontainers.
- 시큐어 코딩: SQL은 파라미터 바인딩(QueryDSL/JPA)만, 비밀번호는 BCrypt, 응답에 해시·시크릿 노출 금지.
- 모듈별 `README.md`에 "왜 이렇게 했는가"를 기록한다.
- Docker 이미지는 Jib Gradle 플러그인으로만 빌드한다(Dockerfile 금지). 대상은 `api-rest`, `api-graphql`, `api-grpc`. `JWT_SECRET`·DB 비밀번호는 이미지에 포함하지 않고 실행 시 환경 변수로 주입한다.

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
- 키워드가 빈 문자열이거나 `%`, `_` 포함 → 전체 조회 또는 리터럴 검색, 오류 없음.

## File Structure

```
settings.gradle, build.gradle, gradle.properties, docker-compose.yml, .gitignore
config/checkstyle/checkstyle.xml
core/    src/main/java/com/benggri/example/core/{common,member,post,security}
         src/main/resources/db/migration/V1__init.sql
         src/test/java/...  (Testcontainers 지원: support/PostgresTestBase)
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
- `MemberService`: `MemberResult register(String email, String rawPassword, String name)`(중복 → `DuplicateException`), `MemberResult get(Long id)`, `Page<MemberResult> list(Pageable)`, `MemberResult update(AuthUser actor, Long id, String name)`, `void delete(AuthUser actor, Long id)`(본인 또는 ADMIN, 아니면 `ForbiddenException`; 없으면 `NotFoundException`).
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
- `POST /auth/login`(`{email,password}`→`TokenPair`), `POST /auth/refresh`, `POST /api/members`(가입, 인증 불필요), `GET/PUT/DELETE /api/members/{id}`, `GET /api/posts?keyword&memberId&page&size`, `POST /api/posts`, `GET/PUT/DELETE /api/posts/{id}`. 목록 응답은 `PageResponse<T>(List<T> items, int page, int size, long total)`.
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

- [ ] **Step 1:** 루트 README에 모듈 지도, 실행 순서(`docker compose up -d` → `JWT_SECRET` 설정 → `bootRun`), 세 프로토콜 호출 예제(curl, GraphQL 쿼리, grpcurl), TDD 워크플로 요약, 모듈을 가져가는 법을 쓴다.
- [ ] **Step 2: Verify.** Run: `./gradlew clean check` → Expected: BUILD SUCCESSFUL(전 모듈 테스트, Checkstyle 위반 0).
- [ ] **Step 3: 시크릿 점검.** Run: `grep -rniE "(password|secret|apikey)\s*[:=]\s*[\"'][^$]" --include=*.java --include=*.yml --include=*.gradle . | grep -v src/test` → Expected: 출력 없음.
- [ ] **Step 3b: Jib 이미지.** 세 api 모듈에 Jib를 설정한다(`from.image=eclipse-temurin:21-jre`, `to.image=benggri/spring-boot-example-<module>:${version}`, 컨테이너 포트 노출, `environment`에는 시크릿 넣지 않음). 공통 설정은 루트 `build.gradle`의 `subprojects` 블록에서 `api-*`에만 적용한다. Run: `./gradlew :api-rest:jibDockerBuild` 후 `docker run --rm -e JWT_SECRET=<32바이트 이상 임의값> -e SPRING_DATASOURCE_URL=... <image>`로 기동 확인, `docker history <image>`와 `docker inspect`에서 시크릿 문자열이 없는지 확인한다. Expected: 세 이미지 모두 빌드·기동 성공, 이미지에 시크릿 없음. 루트 README에 이미지 빌드·실행법을 추가한다.
- [ ] **Step 4:** `/claude-md-management:revise-claude-md`로 `CLAUDE.md`를 작성·개선한다(빌드·테스트 명령, 모듈 구조, 가이드).
- [ ] **Step 5: Commit** `docs: add readmes and claude.md`.
