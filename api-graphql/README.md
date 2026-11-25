# api-graphql

GraphQL + Spring Security(JWT) 어댑터 모듈입니다. 비즈니스 규칙(소유권 검사, 중복 검사 등)은 모두 `core` 서비스에 있고, 이 모듈은 GraphQL 변환·입력 검증·인증 연결만 담당합니다. 리졸버는 서비스 호출만 하며 엔티티를 응답으로 내보내지 않습니다.

## 왜 이렇게 했는가

- **인증 모델 (익명 뮤테이션 vs 연산 단위 인증)**: `register`/`login`/`refresh` 는 토큰 없이 호출해야 하는데 GraphQL 은 모든 연산이 `POST /graphql` 하나로 들어옵니다. 그래서 URL 수준에서는 `POST /graphql` 만 열고(`permitAll`), 나머지 모든 연산은 리졸버 메서드의 `@PreAuthorize("isAuthenticated()")`(메서드 보안)로 막습니다. 토큰 없이 보호된 연산을 호출하면 HTTP 200 + `extensions.code = UNAUTHENTICATED` GraphQL 오류입니다. `ResolverSecurityGuardTest` 가 "인증 없이 열린 연산은 register/login/refresh 뿐"임을 고정해, 새 연산에 가드를 빼먹으면 테스트가 실패합니다.
- **잘못된 토큰은 익명으로 떨어지지 않음**: `Authorization: Bearer ...` 헤더가 있으면 리소스 서버 필터가 먼저 검증합니다. 만료/변조/refresh 타입/`role` 없는 토큰은 (익명 뮤테이션이라도) HTTP 401 `application/problem+json` 입니다. `/graphiql`, `/actuator` 등 다른 경로는 인증 필요(401)이고 CORS 는 열지 않았습니다.
- **CSRF 비활성**: 세션과 쿠키를 쓰지 않고 매 요청의 Bearer 헤더로만 인증하므로 브라우저가 자동 첨부하는 자격 증명이 없어 CSRF 경로가 없습니다. (쿠키 인증으로 바꾸면 반드시 다시 켜야 합니다.)
- **Author vs Member (개인정보)**: 회원의 이메일은 본인 또는 ADMIN 만 읽을 수 있습니다(`member(id)`). 그래서 `Post.author` 는 `Member` 가 아니라 `id`/`name` 만 가진 `Author` 타입이며, `author { email }` 은 스키마 검증 단계에서 거부됩니다(테스트 `post_authorSelectionOfEmailIsRejectedBySchema`). 회원 목록 쿼리는 스키마에 없고, `passwordHash` 를 노출하는 필드도 없습니다.
- **오류 코드 / 일반 메시지**: `DataFetcherExceptionResolver`(`GraphqlExceptionResolver`)가 코드별 고정 메시지만 응답합니다. 예외 메시지(이메일·비밀번호 포함 가능)·거부된 입력값·스택 트레이스는 응답에 절대 담지 않고, 예기치 못한 예외는 서버 로그에만 ERROR 로 남깁니다(요청 변수는 기록하지 않음).
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
| `BAD_REQUEST` | 검증 실패, 잘못된/빈 ID, 잘못된 페이지 인자, 문법·스키마 검증 오류 | graphql-java 오류는 자체 메시지, 그 외 `Invalid request` |
| `INTERNAL_ERROR` | 그 외 모든 예외 | `Internal error` |

잘못된 Bearer 토큰은 GraphQL 오류가 아니라 HTTP 401 입니다. 문법·스키마 오류의 메시지는 graphql-java 표준이며 값은 담지 않습니다.

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
export $(grep -v '^#' .env | xargs)
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
- 전체 구성: [`monitoring/README.md`](../monitoring/README.md)
