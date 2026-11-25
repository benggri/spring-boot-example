# api-grpc

gRPC + JWT(메타데이터) 어댑터 모듈입니다. 비즈니스 규칙(소유권 검사, 중복 검사 등)은 모두 `core` 서비스에 있고, 이 모듈은 proto 변환·입력 검증·인증 연결·오류 매핑만 담당합니다. 모든 RPC 는 `core` 서비스를 호출하며 엔티티를 응답으로 내보내지 않습니다.

스택은 Boot 네이티브 `spring-boot-starter-grpc-server` (grpc-java 1.84.1, protobuf 4.36.2, 모두 Boot BOM 기준)이고, 테스트는 `spring-boot-starter-grpc-server-test` 의 in-process 전송을 씁니다.

## 왜 이렇게 했는가

- **인증: 메타데이터 + gRPC Context (SecurityContextHolder 아님)**: `JwtServerInterceptor` 가 `authorization: Bearer <access token>` 메타데이터를 `JwtProvider.parseAccess`(서명, 만료, `typ=access`, `sub`/`role` 존재)로 검증하고 `AuthUser` 를 `io.grpc.Context` 에 넣은 뒤 `Contexts.interceptCall` 안에서 호출을 진행합니다. 서비스 구현은 `AuthContext.current()` 만 읽습니다. gRPC 콜백은 여러 스레드에서 실행될 수 있고 `SecurityContextHolder`(ThreadLocal)는 전파되지 않으므로, gRPC 가 직접 전파하는 Context 를 쓰는 것이 안전합니다. `core` 의 `CurrentUser` 도 쓰지 않습니다. 인터셉터는 `@GlobalServerInterceptor` 로 모든 서비스에 전역 등록됩니다.
- **공개 메서드 화이트리스트**: 토큰 없이 호출 가능한 것은 `JwtServerInterceptor.PUBLIC_METHODS` 의 `AuthService/Login`, `AuthService/Refresh`, `MemberService/Register` 셋뿐이며 나머지는 기본 보호입니다. `onlyThreePublicMethodsAreAccessibleWithoutToken` 이 등록된 모든 서비스의 모든 메서드를 열거해 토큰 없이 호출해 보고 위 셋만 `UNAUTHENTICATED` 가 아님을 고정하므로, 새 RPC 는 자동으로 보호되고 목록에 넣지 않으면 테스트도 통과합니다(실수로 공개하면 실패).
- **잘못된 토큰은 익명으로 떨어지지 않음**: 만료/변조/형식 오류/refresh 타입/`role` 없는 토큰, `Bearer` 가 아닌 스킴, 빈 토큰, `authorization` 헤더 중복은 (공개 메서드라도) `UNAUTHENTICATED` 입니다. 상태 설명은 항상 고정 문자열 `Unauthenticated` 이며 토큰이나 파서 메시지를 담지 않습니다. 호출은 핸들러를 실행하지 않고 닫힙니다.
- **proto3 기본값 함정**: 설정하지 않은 스칼라는 0/빈 문자열로 도착합니다. 규칙(`post.proto` 주석에도 있음): `Search` 의 `size == 0` 은 기본 20, `page` 0 은 첫 페이지, `member_id` 는 `optional` 이라 미설정이면 필터 없음(`hasMemberId()`), 공백 `keyword` 는 키워드 없음, 음수 `page`/`size` 는 `INVALID_ARGUMENT`, 100 초과 `size` 는 거부하지 않고 서비스가 100 으로 제한합니다(응답 `size` 가 실제 적용값). `Get`/`Update`/`Delete` 의 `id <= 0`(미설정 0 포함)은 `INVALID_ARGUMENT` 입니다.
- **입력 검증 (`RequestValidator`)**: proto 에는 Bean Validation 이 없어 어댑터에서 REST/GraphQL 과 같은 한도를 직접 검사합니다: 이메일 공백 불가, 형식, 254자 이하 / 비밀번호 8..72 문자(`core` 가 72 UTF-8 바이트 초과도 `INVALID_ARGUMENT` 로 거부) / 이름 공백 불가 50자 이하 / 제목 200자 / 내용 10000자. 로그인은 이메일 254, 비밀번호 288, refresh 토큰은 4096자까지 허용합니다. 위반은 필드 이름만 담은 `INVALID_ARGUMENT`(`title: invalid`)이고 거부된 값은 절대 담지 않습니다.
- **Author vs Member (개인정보)**: 회원 이메일은 본인 또는 ADMIN 만 읽을 수 있습니다(`MemberService/Get`, `core` 가 강제). 그래서 `PostResponse` 의 작성자는 `id`/`name` 만 가진 `Author` 이고 이메일·역할은 없습니다(`postMessage_hasNoEmailOrPasswordFields` 가 PostResponse 에서 닿는 모든 메시지를 검사). 회원 목록 RPC 는 아예 없고 응답 어디에도 `password`/`password_hash` 필드가 없습니다. 작성자는 `Get`/`Search` 모두 요청당 `MemberService.findAuthors` 한 번으로 해소합니다(테스트 `search_authorsLoadedWithoutNPlusOne`: 작성자 5명, 게시글 10개에서 SQL 4개 이하 단언. 실제는 페이지 + count + 작성자 일괄 = 3개).
- **오류 매핑 (`GrpcExceptionMapper`, 한 곳)**: Boot 의 전역 예외 인터셉터에 `GrpcExceptionHandler` 빈으로 연결됩니다. 상태 설명은 코드별 고정 문자열이고 예외 메시지(이메일·비밀번호 포함 가능), 스택 트레이스, 트레일러/상세는 보내지 않습니다. 예기치 못한 예외는 서버 로그에만 ERROR 로 남기고(요청 내용은 기록하지 않음) 클라이언트는 `INTERNAL: Internal error` 를 받습니다.
- **메시지 크기 제한**: `spring.grpc.server.inbound.message.max-size: 256KB` (게시글 내용 최대 10000자는 UTF-8 로 약 40KB), 메타데이터는 8KB. 큰 요청으로 메모리를 소모시키지 못하게 하는 상한이며 초과 시 grpc-java 가 `RESOURCE_EXHAUSTED` 로 거부합니다.
- **평문 gRPC**: 이 예제는 localhost 에서 평문(TLS 없음)으로 서빙합니다. JWT 를 평문으로 보내는 것은 로컬에서만 허용되며 운영에서는 반드시 TLS 가 필요합니다(`spring.grpc.server.ssl.*` 번들 설정 또는 TLS 를 종단하는 프록시).
- **리플렉션은 기본 꺼짐**: 서비스 목록을 노출하지 않기 위해 `spring.grpc.server.reflection.enabled=false` 입니다. 로컬 grpcurl 편의를 위해 `SPRING_GRPC_SERVER_REFLECTION_ENABLED=true` 로 켜거나 `-proto` 옵션을 쓰세요.
- **TCP 검증**: `GrpcNettyTransportTest` 가 실제 Netty 서버(무작위 포트, 평문)로 토큰 없는 호출 거부, 로그인 후 호출, 256KB 초과 메시지의 `RESOURCE_EXHAUSTED` 를 확인합니다. 그 외 테스트는 in-process 전송입니다.
- **헬스 서비스**: Boot 가 등록하는 `grpc.health.v1.Health` 도 전역 인터셉터 대상이라 JWT 가 필요합니다. 프로브용으로 열려면 `PUBLIC_METHODS` 를 의도적으로 늘리고 테스트도 갱신하세요.
- **웹 서버 없음**: `spring.main.web-application-type: none`. (액추에이터 HTTP 포트는 이후 작업에서 추가합니다.)

## 상태 코드

| gRPC 상태 | 원인 | 설명(description) |
|---|---|---|
| `UNAUTHENTICATED` | 토큰 없이 보호된 RPC, 유효하지 않은 토큰, 로그인 실패, 잘못된 refresh 토큰 | `Unauthenticated` |
| `PERMISSION_DENIED` | 타인의 게시글 수정/삭제, 타인의 회원 조회 등 | `Permission denied` |
| `NOT_FOUND` | 없는 게시글/회원 | `Not found` |
| `ALREADY_EXISTS` | 이미 가입된 이메일 | `Already exists` |
| `INVALID_ARGUMENT` | 검증 실패, `id <= 0`, 음수 page/size, 72바이트 초과 비밀번호 | `<field>: invalid` 또는 `Invalid argument` |
| `INTERNAL` | 그 외 모든 예외 | `Internal error` |

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
./gradlew :api-grpc:bootRun
```

gRPC 포트는 9190(`spring.grpc.server.port`)입니다.

## grpcurl 예시

리플렉션 서비스도 전역 인터셉터 대상이라 토큰이 필요합니다(기본은 꺼짐). 그래서 토큰이 필요 없는 Register/Login 은 `-proto`/`-import-path` 로 호출합니다.

```bash
H=localhost:9190
P="-plaintext -import-path api-grpc/src/main/proto"

# 가입 (토큰 불필요)
grpcurl $P -proto member.proto -d '{"email":"a@example.com","password":"change-me-please","name":"Alice"}' $H example.v1.MemberService/Register

# 로그인 (토큰 불필요) -> accessToken
TOKEN=$(grpcurl $P -proto auth.proto -d '{"email":"a@example.com","password":"change-me-please"}' $H example.v1.AuthService/Login | jq -r .accessToken)

# 이후 호출은 authorization 메타데이터 필요
grpcurl $P -proto post.proto -H "authorization: Bearer $TOKEN" -d '{"title":"hi","content":"hello"}' $H example.v1.PostService/Create
grpcurl $P -proto post.proto -H "authorization: Bearer $TOKEN" -d '{"page":0,"size":10}' $H example.v1.PostService/Search
```

리플렉션을 켠 경우(`SPRING_GRPC_SERVER_REFLECTION_ENABLED=true`)에는 `-proto` 대신 `-reflect-header "authorization: Bearer $TOKEN"` 을 함께 주면 `list`/`describe` 가 동작합니다.

## 입력 정제 (NUL 문자)

proto3 문자열은 U+0000 을 담을 수 있지만 PostgreSQL 은 저장/조회할 수 없어 드라이버 예외(서버 오류)가 됩니다. `core` 의 `TextGuard` 가 SQL 로 가는 모든 클라이언트 문자열(이메일, 이름, 제목, 내용, 검색 키워드)에서 NUL 과 탭/개행을 제외한 C0 제어문자를 저장소 호출 전에 거부하고(`InvalidRequestException`, `INVALID_ARGUMENT`), 검색 키워드는 200자로 제한합니다. 빈 비밀번호도 `core` 가 거부합니다.

## 운영 하드닝 메모

예제는 keepalive, 동시 호출 수(`max-concurrent-calls`), 호출 데드라인 제한을 설정하지 않았습니다. 운영에서는 TLS 와 함께 이 제한들을 추가하세요.

## proto 재생성 / 확인

proto 는 `src/main/proto/{auth,member,post}.proto` (패키지 `example.v1`)입니다. 자바 코드는 `com.google.protobuf` 플러그인이 빌드할 때 `build/generated/sources/proto` 에 생성하며 커밋하지 않습니다(`protoc`/`protoc-gen-grpc-java` 버전은 `gradle.properties`). 다시 생성: `./gradlew :api-grpc:generateProto`. 생성 소스는 Checkstyle 대상이 아닙니다.

## 모니터링 (Prometheus)

| 항목 | 값 |
|---|---|
| gRPC 포트 | 9190 |
| HTTP(actuator) 포트 | 8083 (`server.port`, 별도 관리 포트 없음) |
| 스크랩 경로 | `http://localhost:8083/actuator/prometheus` |

- 노출 엔드포인트는 `health`, `prometheus` 뿐입니다 (`management.endpoints.web.exposure.include`). `env`, `beans`, `metrics`, `info`, `loggers`, `heapdump` 등은 노출하지 않으며 테스트로 확인합니다.
- `management.endpoint.health.show-details: never` / `show-components: never`: 응답은 `{"status":"UP"}` 뿐입니다.
- 보안: 관리 포트는 인증 없이 열려 있으므로 **외부에 공개하면 안 됩니다** (방화벽/내부 네트워크 전용, 컨테이너에서는 게시하지 않기). Prometheus 는 같은 호스트/내부망에서만 scrape 합니다.
- 커스텀 메트릭 `app.service.invocation` (Prometheus: `app_service_invocation_seconds_*`): 태그는 `class`, `method`, `outcome`(`success`|`failure`) 세 개뿐이며 인자, 이메일, 사용자 ID, 예외 메시지는 절대 태그/값에 넣지 않습니다.
- 이 앱에는 Spring Security 웹 필터 체인과 서블릿 컨테이너가 없었으므로, actuator 가 HTTP 를 서비스할 수 있게 `spring-boot-starter-webmvc` 만 추가하고 `spring.main.web-application-type: servlet`, `server.port: 8083` 으로 설정했습니다 (`spring-boot-starter-security` 는 없으므로 생성된 비밀번호/기본 로그인도 없습니다).
- gRPC 표준 `grpc.health.v1.Health` 서비스는 계속 JWT 인터셉터로 보호됩니다 (공개하지 않음). 프로브는 `http://localhost:8083/actuator/health` 를 사용하세요.
- 전체 구성: [`monitoring/README.md`](../monitoring/README.md)
