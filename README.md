# spring-boot-example

Spring Boot 4.2 멀티모듈 예제입니다. 같은 도메인(회원, 게시글)을 REST, GraphQL, gRPC 세 가지 프로토콜과 배치로 제공하며, 비즈니스 로직은 공유 라이브러리 `core` 한 곳에 둡니다.

## 목적과 대상

- **학습용**: 인증(JWT), 영속성(JPA + QueryDSL + Flyway), 세 가지 API 프로토콜, 배치, 모니터링을 한 저장소에서 비교하며 읽습니다.
- **팀 참고 템플릿**: 모듈 단위로 가져가 쓸 수 있는 구조와 보안/테스트 규칙의 기준선입니다.
- **신규 프로젝트 가이드**: AI 에이전트와 함께 만든 개발 흐름(Spec-driven + Subagent-driven)을 [AI-DLC 가이드](docs/ai-dlc/GUIDE.md)에 정리했습니다.

## 모듈 지도

| 모듈 | 역할 | 포트 | README |
|---|---|---|---|
| `core` | 공유 라이브러리: 엔티티, 저장소(JPA + QueryDSL), 서비스(비즈니스 규칙), JWT, Flyway 마이그레이션, AOP 로깅 | - | (이 문서) |
| `api-rest` | REST + Spring Security(JWT) + Swagger | HTTP 8080, 관리(actuator) 8081 | [api-rest/README.md](api-rest/README.md) |
| `api-graphql` | GraphQL(`POST /graphql`) + JWT | HTTP 8090, 관리(actuator) 8082 | [api-graphql/README.md](api-graphql/README.md) |
| `api-grpc` | gRPC + JWT(메타데이터) | gRPC 9190, actuator HTTP 8083 | [api-grpc/README.md](api-grpc/README.md) |
| `batch` | Spring Batch 잡 `memberPostStatsJob`(회원별 일일 게시글 수 집계), 실행 후 종료 | 없음(웹 서버 없음) | [batch/README.md](batch/README.md) |
| `monitoring/` | Prometheus 설정(compose 프로필 `monitoring`) | 127.0.0.1:9090 | [monitoring/README.md](monitoring/README.md) |

관리(actuator) 포트는 인증이 없으므로 외부에 공개하지 마세요.

## 아키텍처

```
 api-rest ----+
 api-graphql -+--> core (서비스 / 저장소 / JWT / Flyway) --> PostgreSQL
 api-grpc ----+
 batch -------+
```

`api-*` 와 `batch` 는 `core` 에만 의존하고 서로 의존하지 않습니다. 소유권 검사 같은 규칙은 `core` 서비스에 있고, 각 어댑터는 프로토콜 변환·입력 검증·인증 연결만 맡습니다. 설계 배경은 [설계 문서](docs/superpowers/specs/2026-11-25-spring-boot-example-design.md)를 참고하세요.

## 기술 스택

Spring Boot 4.2.0, Java 21, Gradle(Groovy DSL), Spring Security + JWT(자체 발급), REST / GraphQL / gRPC, JPA + QueryDSL, Flyway, PostgreSQL 17, Swagger(OpenAPI), AOP, Lombok, Prometheus(Micrometer), Jib, Spring Batch, Testcontainers, TDD.

## 빠른 시작

사전 준비: JDK 21(Gradle 툴체인이 사용), Docker.

```bash
cp .env.example .env
# .env 를 열어 POSTGRES_PASSWORD 를 바꾸고 JWT_SECRET 을 채웁니다 (32바이트 이상).
# JWT_SECRET 생성 예: openssl rand -base64 48
docker compose up -d          # PostgreSQL 17 (호스트 5432)
```

`docker compose` 는 저장소 루트의 `.env` 를 자동으로 읽습니다. 앱은 `.env` 를 읽지 않으므로 같은 변수를 셸 환경에 내보내야 합니다.

```bash
set -a; source .env; set +a   # .env 의 값을 현재 셸에 export
./gradlew :api-rest:bootRun       # 8080 (관리 8081)
./gradlew :api-graphql:bootRun    # 8090 (관리 8082)
./gradlew :api-grpc:bootRun       # gRPC 9190 (actuator 8083)
```

각각 별도 터미널에서 실행합니다. 환경 변수:

| 이름 | 필수 | 설명 |
|---|---|---|
| `POSTGRES_PASSWORD` | 예 | DB 비밀번호. 기본값이 없어 비어 있으면 compose 와 앱이 시작되지 않습니다 |
| `JWT_SECRET` | 예(`batch` 제외) | HS256 서명 키, 32바이트 이상. 기본값 없음 |
| `DB_URL` | 아니오 | 기본 `jdbc:postgresql://localhost:5432/example` |
| `DB_USERNAME` | 아니오 | 기본 `example` |
| `POSTGRES_HOST_PORT` | 아니오 | compose 의 PostgreSQL 호스트 포트, 기본 5432 |

## 테스트 실행

```bash
./gradlew check               # 전 모듈 테스트 + Checkstyle
./gradlew :core:test          # 모듈 하나만
```

테스트는 Testcontainers 로 실제 PostgreSQL 컨테이너를 띄우므로 **Docker 가 실행 중이어야 합니다**. Checkstyle 은 메서드 60줄, 파일 350줄 한도를 넘으면(경고 0 허용) 빌드가 실패합니다. 설정은 `config/checkstyle/checkstyle.xml` 입니다.

## 호출 예제

앱을 실행한 뒤(위 빠른 시작) 같은 회원/게시글을 세 프로토콜로 호출합니다. 이메일·비밀번호는 예시 값입니다(비밀번호는 8~72자).

### REST (curl)

```bash
B=http://localhost:8080
curl -s -X POST $B/api/members -H 'Content-Type: application/json' \
  -d '{"email":"a@example.com","password":"change-me-please","name":"Alice"}'        # 201
TOKEN=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@example.com","password":"change-me-please"}' | jq -r .accessToken)
curl -s -X POST $B/api/posts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"hi","content":"hello"}'                                              # 201
curl -s "$B/api/posts?page=0&size=10" -H "Authorization: Bearer $TOKEN"              # 200
```

토큰 없이 `GET /api/posts` 를 호출하면 401 입니다.

### GraphQL (`POST /graphql`)

```bash
URL=http://localhost:8090/graphql
gql() { curl -s "$URL" -H 'Content-Type: application/json' "${@:2}" -d "$1"; }
gql '{"query":"mutation($i:RegisterInput!){register(input:$i){id email name role}}","variables":{"i":{"email":"a@example.com","password":"change-me-please","name":"Alice"}}}'
TOKEN=$(gql '{"query":"mutation($i:LoginInput!){login(input:$i){accessToken}}","variables":{"i":{"email":"a@example.com","password":"change-me-please"}}}' | jq -r .data.login.accessToken)
gql '{"query":"mutation{createPost(input:{title:\"hi\",content:\"hello\"}){id author{name}}}"}' -H "Authorization: Bearer $TOKEN"
gql '{"query":"{posts(page:0,size:10){items{id title author{id name}} total}}"}' -H "Authorization: Bearer $TOKEN"
```

### gRPC (grpcurl)

> **grpcurl 미검증**: 이 저장소 작성 환경에는 grpcurl 이 설치되어 있지 않았습니다. 아래 인자(`-plaintext`, `-import-path`, `-proto`, `-H`)와 서비스 이름은 `fullstorydev/grpcurl` Docker 이미지로 컨테이너의 gRPC 서버에 호출해 확인했지만, 로컬 grpcurl 바이너리로 그대로 실행해 본 것은 아닙니다. 서버 리플렉션은 꺼져 있고 Register/Login 외에는 토큰이 필요하므로 `-proto` 를 씁니다.

```bash
H=localhost:9190
P="-plaintext -import-path api-grpc/src/main/proto"
grpcurl $P -proto member.proto -d '{"email":"a@example.com","password":"change-me-please","name":"Alice"}' $H example.v1.MemberService/Register
TOKEN=$(grpcurl $P -proto auth.proto -d '{"email":"a@example.com","password":"change-me-please"}' $H example.v1.AuthService/Login | jq -r .accessToken)
grpcurl $P -proto post.proto -H "authorization: Bearer $TOKEN" -d '{"title":"hi","content":"hello"}' $H example.v1.PostService/Create
grpcurl $P -proto post.proto -H "authorization: Bearer $TOKEN" -d '{"page":0,"size":10}' $H example.v1.PostService/Search
```

## Swagger / 모니터링 / 배치

- **Swagger UI**: api-rest 실행 후 http://localhost:8080/swagger-ui.html (OpenAPI 문서는 `/v3/api-docs`).
- **모니터링**: 세 앱이 `/actuator/prometheus` 를 노출합니다(api-rest 8081, api-graphql 8082, api-grpc 8083). Prometheus 는 compose 프로필 `monitoring` 으로 띄웁니다: `docker compose --profile monitoring up -d prometheus` (UI `http://127.0.0.1:9090`). 자세한 구성과 PromQL 은 [monitoring/README.md](monitoring/README.md). 서비스 호출 메트릭 `app_service_invocation_seconds_*` 는 AOP 로 기록됩니다.
- **배치**: 하루치 게시글을 회원별로 집계하는 잡 `memberPostStatsJob` 을 실행하고 종료합니다. `JWT_SECRET` 은 필요 없습니다.

```bash
set -a; source .env; set +a
./gradlew :batch:bootRun --args='--spring.batch.job.name=memberPostStatsJob runDate=2026-11-25'
```

종료 코드는 성공 0, `runDate` 형식 오류와 이미 완료된 같은 `runDate` 재실행은 1, 스텝 실패는 5 입니다. 자세한 내용은 [batch/README.md](batch/README.md).

## 컨테이너 이미지 (Jib)

api-rest, api-graphql, api-grpc, batch 는 Jib 으로 Docker 데몬에 이미지를 만듭니다(Dockerfile 없음). 기반 이미지는 `eclipse-temurin:21-jre`, 이미지 이름은 `benggri/spring-boot-example-<모듈>:0.0.1-SNAPSHOT` 입니다. 설정은 루트 `build.gradle` 에 있습니다.

```bash
./gradlew :api-rest:jibDockerBuild :api-graphql:jibDockerBuild :api-grpc:jibDockerBuild :batch:jibDockerBuild
```

이미지에는 시크릿이 들어 있지 않습니다. `JWT_SECRET`, `POSTGRES_PASSWORD`, `DB_URL` 은 실행할 때 환경 변수로 넘깁니다. DB 가 호스트에서 돌고 있으면 `host.docker.internal` 을 호스트로 연결합니다(아래 `DB_URL` 의 포트는 호스트에 게시된 PostgreSQL 포트로 바꿉니다).

```bash
set -a; source .env; set +a
docker run --rm --add-host=host.docker.internal:host-gateway -p 8080:8080 \
  -e JWT_SECRET -e POSTGRES_PASSWORD \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/example \
  benggri/spring-boot-example-api-rest:0.0.1-SNAPSHOT
# api-graphql: -p 8090:8090 / api-grpc: -p 9190:9190 (JWT_SECRET 필요)

# batch: JWT_SECRET 불필요, 잡 인자를 그대로 뒤에 붙입니다. 실행 후 컨테이너가 종료됩니다.
docker run --rm --add-host=host.docker.internal:host-gateway \
  -e POSTGRES_PASSWORD -e DB_URL=jdbc:postgresql://host.docker.internal:5432/example \
  benggri/spring-boot-example-batch:0.0.1-SNAPSHOT \
  --spring.batch.job.name=memberPostStatsJob runDate=2026-11-25
```

`-e NAME`(값 없이)은 셸에 export 된 값을 전달하므로 명령 기록에 값이 남지 않습니다. 관리(actuator) 포트 8081/8082/8083 은 인증이 없으므로 외부에 `-p` 로 게시하지 마세요. 컨테이너 안의 앱은 root 로 실행됩니다(Jib 기본값, `container.user` 미설정).

## 개발 흐름

이 저장소는 TDD(Red → Green → Refactor)로 만들었습니다. 실패하는 테스트를 먼저 쓰고, 통과시키는 최소 구현을 한 뒤 정리하며, 테스트와 구현은 Green 상태에서 한 커밋으로 남깁니다. 버그는 재현 테스트부터 씁니다. 요구사항 → spec → 계획 → Task별 구현/리뷰 전체 흐름과 규칙은 [docs/ai-dlc/GUIDE.md](docs/ai-dlc/GUIDE.md), 설계는 [설계 문서](docs/superpowers/specs/2026-11-25-spring-boot-example-design.md) 에 있습니다. 이 프로젝트의 CLAUDE.md 는 `claude-md-management` 스킬로 관리할 예정입니다.

## 모듈을 내 프로젝트로 가져가기

1. 필요한 어댑터 모듈 디렉터리와 `core` 를 함께 복사합니다(`api-*`/`batch` 는 `core` 에 의존합니다).
2. `settings.gradle` 의 `include`, 루트 `build.gradle`, `gradle.properties`(버전), `config/checkstyle/`, `lombok.config` 를 가져가 맞춥니다.
3. 패키지(`com.benggri.example`), `group`, Jib 이미지 이름(`build.gradle` 의 `to.image`)을 바꿉니다.
4. 환경 변수(`POSTGRES_PASSWORD`, `JWT_SECRET`, `DB_URL`)와 `docker-compose.yml`, `.env.example` 을 같이 가져가고 `.env` 는 커밋하지 않습니다.
5. 모듈 README 의 "왜 이렇게 했는가" 를 읽고 보안 결정(인증 경로, 오류 형식, 관리 포트)을 내 요구에 맞게 검토합니다.

## 문제 해결

| 증상 | 원인과 해결 |
|---|---|
| `port is already allocated` / `5432 already in use` | 호스트 5432 를 다른 PostgreSQL 이 쓰는 경우입니다. `POSTGRES_HOST_PORT=55433` 을 `.env` 나 셸에 설정하고 앱에는 `DB_URL=jdbc:postgresql://localhost:55433/example` 을 함께 지정합니다. |
| 8080/8081/8082/8083/8090/9190 이 이미 사용 중 | 해당 포트를 쓰는 프로세스를 종료하거나 `docker run -p <다른 호스트 포트>:<컨테이너 포트>` 로 매핑합니다. 로컬 실행은 포트가 `application.yml` 에 고정입니다. |
| `docker compose` 가 `set POSTGRES_PASSWORD` 로 실패 | `.env` 가 없거나 `POSTGRES_PASSWORD` 가 비어 있습니다. `cp .env.example .env` 후 값을 확인합니다. |
| 앱이 `app.jwt.secret must be at least 32 bytes` 로 시작 실패 | `JWT_SECRET` 이 비어 있거나 32바이트 미만입니다(배치는 불필요). `set -a; source .env; set +a` 를 같은 셸에서 실행했는지, `.env` 의 `JWT_SECRET` 값이 채워져 있는지 확인합니다. |
| 앱이 `password authentication failed for user "example"` 로 시작 실패 | `POSTGRES_PASSWORD` 가 셸에 export 되지 않았거나 DB 를 처음 만들 때의 값과 다릅니다(볼륨에 남은 이전 비밀번호). 값을 맞추거나 볼륨을 지우고 다시 만듭니다. |
| 컨테이너에서 DB 연결 실패 | 컨테이너의 `localhost` 는 컨테이너 자신입니다. `--add-host=host.docker.internal:host-gateway` 와 `DB_URL` 의 호스트를 `host.docker.internal` 로 지정합니다. |
| `./gradlew check` 의 DB 테스트가 시작 실패 | Testcontainers 가 Docker 를 필요로 합니다. Docker 데몬이 실행 중인지 확인합니다. |
