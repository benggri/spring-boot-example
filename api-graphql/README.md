# api-graphql

GraphQL + Spring Security(JWT) 어댑터 모듈입니다. 비즈니스 규칙(소유권 검사, 중복 검사 등)은 모두 `core` 서비스에 있고, 이 모듈은 GraphQL 변환·입력 검증·인증 연결만 담당합니다. 리졸버는 서비스 호출만 하며 엔티티를 응답으로 내보내지 않습니다.

## 왜 이렇게 했는가

- **인증 모델 (익명 뮤테이션 vs 연산 단위 인증)**: `register`/`login`/`refresh` 는 토큰 없이 호출해야 하는데 GraphQL 은 모든 연산이 `POST /graphql` 하나로 들어옵니다. 그래서 URL 수준에서는 `POST /graphql` 만 열고(`permitAll`), 나머지 모든 연산은 리졸버 메서드의 `@PreAuthorize("isAuthenticated()")`(메서드 보안)로 막습니다. 토큰 없이 보호된 연산을 호출하면 HTTP 200 + `extensions.code = UNAUTHENTICATED` GraphQL 오류입니다. `ResolverSecurityGuardTest` 가 애플리케이션 컨텍스트의 **모든** `@Controller` 빈을 찾아, `@QueryMapping`/`@MutationMapping`/`@SubscriptionMapping`(및 Query/Mutation/Subscription 타입의 `@SchemaMapping`/`@BatchMapping`) 핸들러 각각이 `@PreAuthorize` 가드를 갖거나 명시적 공개 집합 {register, login, refresh} 에 속함을 고정합니다. 그래서 새 컨트롤러·새 연산에 가드를 빼먹으면 테스트가 실패합니다. 검사 로직 자체(`ResolverGuards`)는 `ResolverGuardsTest` 에서 가짜 클래스로 검증하며, 그 가짜 클래스는 운영 컨텍스트에 스캔되지 않습니다. `POST /graphql` 은 URL 수준에서 열려 있는(allow-by-default) 구조이므로 이 테스트가 유일한 안전망입니다.
- **인자 바인딩과 검증은 메서드 보안보다 먼저**: 인자 바인딩과 `@Valid` 는 메서드 보안 프록시보다 먼저 실행됩니다. 그래서 익명 호출자가 보호된 연산에 형식이 틀린 입력을 보내면 `UNAUTHENTICATED` 가 아니라 `BAD_REQUEST` 를 받습니다. 드러나는 것은 검증 규칙뿐이며 데이터나 권한 정보는 아닙니다.
- **회원 수정/삭제 뮤테이션은 일부러 제공하지 않음**: REST 와 gRPC 는 회원 수정/삭제가 있지만 GraphQL 은 `register`/`login`/`refresh`/`member(id)` 만 있습니다. 게시글 CRUD 는 GraphQL 에서도 완전합니다(`createPost`/`updatePost`/`deletePost`/`post`/`posts`).
- **잘못된 토큰은 익명으로 떨어지지 않음**: `Authorization: Bearer ...` 헤더가 있으면 리소스 서버 필터가 먼저 검증합니다. 만료/변조/refresh 타입/`role` 없는 토큰은 (익명 뮤테이션이라도) HTTP 401 `application/problem+json` 입니다. `/graphiql`, `/actuator` 등 다른 경로는 인증 필요(401)이고 CORS 는 열지 않았습니다.
- **CSRF 비활성**: 세션과 쿠키를 쓰지 않고 매 요청의 Bearer 헤더로만 인증하므로 브라우저가 자동 첨부하는 자격 증명이 없어 CSRF 경로가 없습니다. (쿠키 인증으로 바꾸면 반드시 다시 켜야 합니다.)
- **Author vs Member (개인정보)**: 회원의 이메일은 본인 또는 ADMIN 만 읽을 수 있습니다(`member(id)`). 그래서 `Post.author` 는 `Member` 가 아니라 `id`/`name` 만 가진 `Author` 타입이며, `author { email }` 은 스키마 검증 단계에서 거부됩니다(테스트 `post_authorSelectionOfEmailIsRejectedBySchema`). 회원 목록 쿼리는 스키마에 없고, `passwordHash` 를 노출하는 필드도 없습니다.
- **오류 코드 / 일반 메시지**: `DataFetcherExceptionResolver`(`GraphqlExceptionResolver`)가 코드별 고정 메시지만 응답합니다. 예외 메시지(이메일·비밀번호 포함 가능)·거부된 입력값·스택 트레이스는 응답에 절대 담지 않고, 예기치 못한 예외는 서버 로그에만 ERROR 로 남깁니다(요청 변수는 기록하지 않음).
- **루트 필드 수 제한**: `MaxRootFieldsInstrumentation`, 기본 10 (`app.graphql.max-root-fields`, 1 이상 검증). 별칭(alias)으로 `login`/`register` 를 한 요청에 수십 번 넣으면 요청 하나가 BCrypt 를 N 번 돌리게 되므로, 연산 하나의 루트 필드 수(별칭·fragment 포함)가 한도를 넘으면 리졸버 실행 전에 `BAD_REQUEST`(고정 메시지 `Too many root fields in one operation`, 질의 내용을 되돌려 주지 않음)로 거부합니다. 요청 수 자체의 속도 제한은 없습니다(로그인/가입 rate limiting 없음: 운영에서는 게이트웨이/리미터를 두세요).
- **질의 깊이 제한**: `MaxQueryDepthInstrumentation`, 기본 10 (`app.graphql.max-query-depth`, 1 이상 검증). 도메인 스키마는 순환이 없어 얕지만, 인트로스펙션의 재귀 타입(`__Type.ofType`)으로 깊은 질의를 만들 수 있어 필요합니다. 인트로스펙션은 켜 둔 상태이며, 운영에서는 `spring.graphql.schema.introspection.enabled=false` 로 끌 수 있습니다. GraphiQL 은 꺼져 있습니다.
- **N+1 방지**: `Post.author` 를 `@BatchMapping` 으로 해소해, 게시글 N 개 목록도 회원 조회는 `MemberService.findAuthors`(`findAllById`) 한 번입니다. 목록 조회 전체가 SQL 3개(게시글 페이지 + count + 작성자 일괄)이며 테스트(`posts_loadsAuthorsWithoutNPlusOne`, Hibernate 통계)는 작성자 5명·게시글 10개에서 4개 이하를 단언합니다.
- **입력 검증**: REST 와 같은 한도(이메일 254, 비밀번호 8..72, 이름 50, 제목 200, 내용 10000)를 Bean Validation 으로 검사하고 위반은 `BAD_REQUEST` 입니다. 72바이트를 넘는 다국어 비밀번호도 `BAD_REQUEST` 입니다. 잘못된 인자도 모두 `BAD_REQUEST`(HTTP 200)이며 INTERNAL_ERROR 나 ERROR 로그가 되지 않습니다: 숫자가 아닌 ID, 빈 문자열 ID(`IdArguments.requireId`), 범위를 넘는 숫자 ID, 음수 `page`, 1 미만 `size`, 그리고 `page`/`size` 에 명시적 `null`(스키마가 `Int!` 이므로 graphql-java 검증 오류이고 `ClientErrorCodeInterceptor` 가 `extensions.code` 를 붙입니다. 익명 호출도 같음). 문법/스키마 검증 오류도 같은 코드를 받습니다. 100 초과 `size` 는 서비스가 100 으로 제한하며, 빈 문자열 `memberId` 는 "필터 없음" 입니다.
- **보안 핸들러 중복**: `error/` 의 401/403 JSON 핸들러와 `SecurityConfig` 는 `api-rest` 의 것을 복사했습니다. 공유 모듈로 빼는 것은 이번 범위 밖입니다. `Clock`/`PasswordEncoder`/`JwtDecoder` 는 `core` 의 빈을 그대로 씁니다.

## 오류 코드

| `extensions.code` | 원인 | message |
|---|---|---|
| `UNAUTHENTICATED` | 토큰 없이 보호된 연산, 로그인 실패, 잘못된 refresh 토큰 | `Unauthenticated` |
| `FORBIDDEN` | 타인의 게시글/회원 접근 등 권한 없음 | `Forbidden` |
| `NOT_FOUND` | 없는 게시글/회원 | `Not found` |
| `CONFLICT` | 이미 가입된 이메일 | `Conflict` |
| `BAD_REQUEST` | 검증 실패, 잘못된/빈 ID, 잘못된 페이지 인자, 문법·스키마 검증 오류 | graphql-java 오류는 자체 메시지(입력 리터럴을 인용할 수 있음), 루트 필드 수 초과는 고정 메시지, 그 외 `Invalid request` |
| `INTERNAL_ERROR` | 그 외 모든 예외 | `Internal error` |

잘못된 Bearer 토큰은 GraphQL 오류가 아니라 HTTP 401 입니다. 문법·스키마 오류의 메시지는 graphql-java 표준입니다. graphql-java 의 검증/값 강제(coercion) 메시지는 클라이언트가 보낸 리터럴을 **같은 클라이언트에게** 되돌려 줄 수 있습니다(서버 로그에는 남기지 않음). 따라서 "메시지에 값이 절대 없다"고 보장하지는 않습니다. 서비스·리졸버가 만드는 오류 메시지는 고정 문구입니다.

## 스키마 요약

`Query`: `post(id)`, `posts(keyword, memberId, page = 0, size = 20)`, `member(id)`(본인/ADMIN). `Mutation`: `register`, `login`, `refresh`, `createPost`, `updatePost`, `deletePost`. 전체는 `src/main/resources/graphql/schema.graphqls`.

## 환경 변수

| 이름 | 필수 | 설명 |
|---|---|---|
| `JWT_SECRET` | 예 | HS256 서명 키, 32바이트 이상 (예: `openssl rand -base64 48`) |
| `POSTGRES_PASSWORD` | 예 | DB 비밀번호 (기본값 없음) |
| `DB_URL` | 아니오 | 기본 `jdbc:postgresql://localhost:5432/example` |
| `DB_USERNAME` | 아니오 | 기본 `example` |

## 실행

```bash
cp .env.example .env        # 값을 채웁니다 (JWT_SECRET 포함)
docker compose up -d
set -a; source .env; set +a
./gradlew :api-graphql:bootRun
```

포트는 8090, 엔드포인트는 `POST http://localhost:8090/graphql` 입니다.

## 예시

```bash
URL=http://localhost:8090/graphql
gql() { curl -s "$URL" -H 'Content-Type: application/json' "${@:2}" -d "$1"; }

# 가입 (익명)
gql '{"query":"mutation($i:RegisterInput!){register(input:$i){id email name role}}","variables":{"i":{"email":"a@example.com","password":"change-me-please","name":"Alice"}}}'

# 로그인 (익명) -> accessToken
TOKEN=$(gql '{"query":"mutation($i:LoginInput!){login(input:$i){accessToken}}","variables":{"i":{"email":"a@example.com","password":"change-me-please"}}}' | jq -r .data.login.accessToken)

# 게시글 작성 / 목록 (Bearer 필요)
gql '{"query":"mutation{createPost(input:{title:\"hi\",content:\"hello\"}){id author{name}}}"}' -H "Authorization: Bearer $TOKEN"
gql '{"query":"{posts(page:0,size:10){items{id title author{id name}} total}}"}' -H "Authorization: Bearer $TOKEN"
```

## 입력 정제 (NUL 문자)

JSON 은 `\u0000` 을 허용하지만 PostgreSQL 은 NUL 문자를 저장/조회할 수 없어 드라이버 예외(500)가 됩니다. `core` 의 `TextGuard` 가 SQL 로 가는 모든 클라이언트 문자열(이메일, 이름, 제목, 내용, 검색 키워드)에서 NUL 과 탭/개행을 제외한 C0 제어문자를 저장소 호출 전에 거부하며 `BAD_REQUEST`(HTTP 200 + errors) 입니다. 검색 키워드는 200자로 제한되고 빈 비밀번호도 `core` 가 거부합니다.

## 모니터링 (Prometheus)

| 항목 | 값 |
|---|---|
| 앱 포트 | 8090 |
| 관리(actuator) 포트 | 8082 (`management.server.port`) |
| 스크랩 경로 | `http://localhost:8082/actuator/prometheus` |

- 노출 엔드포인트는 `health`, `prometheus` 뿐입니다 (`management.endpoints.web.exposure.include`). `env`, `beans`, `metrics`, `info`, `loggers`, `heapdump` 등은 노출하지 않으며 테스트로 확인합니다.
- `management.endpoint.health.show-details: never` / `show-components: never`: 응답은 `{"status":"UP"}` 뿐입니다.
- 보안: 관리 포트는 인증 없이 열려 있으므로 **외부에 공개하면 안 됩니다** (방화벽/내부 네트워크 전용, 컨테이너에서는 게시하지 않기). Prometheus 는 같은 호스트/내부망에서만 scrape 합니다.
- 커스텀 메트릭 `app.service.invocation` (Prometheus: `app_service_invocation_seconds_*`): 태그는 `class`, `method`, `outcome`(`success`|`failure`) 세 개뿐이며 인자, 이메일, 사용자 ID, 예외 메시지는 절대 태그/값에 넣지 않습니다.
- api-rest 와 같은 이유로 `ManagementSecurityConfig`(`@Order(1)`)가 관리 포트의 `health`, `prometheus` 만 permitAll, 나머지 actuator 는 denyAll 합니다. 메인 체인(`/graphql` 보호)은 변하지 않으며 테스트로 확인합니다.
- 포트별 응답(테스트로 고정): 앱 포트의 `/actuator/**` 는 익명 401, 유효한 토큰이 있으면 404 입니다. 관리 포트의 `/api/posts`, `/auth/login`, `/graphql` 은 토큰이 있으면 404, 익명이면 401 입니다(앱 엔드포인트를 서비스하지 않음). 404 본문은 Boot 기본 오류 JSON 이며 요청 경로를 같은 클라이언트에게 되돌려 줄 뿐 트레이스/메시지는 없습니다.
- `/actuator` 링크(discovery) 페이지는 꺼져 있습니다(`management.endpoints.web.discovery.enabled: false`): 노출된 엔드포인트 목록을 알려 주지 않습니다. 테스트가 `/actuator` 와 `/actuator/` 가 200 이 아님을 고정합니다.
- `management.endpoint.health.probes.enabled: false`: 응답 본문이 정확히 `{"status":"UP"}` 이도록 두었고, Kubernetes 용 liveness/readiness 그룹(`/actuator/health/liveness` 등)은 제공하지 않습니다. 쿠버네티스 프로브가 필요하면 이 값을 켜고 테스트를 함께 바꿔야 합니다.
- 바인드 주소: 관리 포트(8082) 는 인증이 없고 이 예제는 `management.server.address` 를 지정하지 않아 모든 인터페이스에 바인딩됩니다. 의도된 트레이드오프입니다: compose 의 Prometheus 컨테이너가 `host.docker.internal`(호스트 게이트웨이 주소)로 호스트의 앱을 scrape 하려면 루프백이 아닌 주소로 바인딩되어 있어야 합니다. 운영에서는 방화벽/내부 네트워크로 막거나 주소를 제한하세요.
- 전체 구성: [`monitoring/README.md`](../monitoring/README.md)
