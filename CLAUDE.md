# spring-boot-example

Spring Boot 4.2.0 / Java 21 / Gradle(Groovy) 멀티모듈 예제. 모듈: `core`(라이브러리), `api-rest`, `api-graphql`, `api-grpc`, `batch`. `api-*`·`batch` → `core` 단방향. 문서: `README.md`, 모듈별 `README.md`, `docs/ai-dlc/GUIDE.md`, spec/plan은 `docs/superpowers/`.

## 명령
- `./gradlew check` — 전체 테스트 + Checkstyle (Docker 필수: Testcontainers PostgreSQL, 약 10분). 모듈 단위는 `:<module>:check`, 단일 클래스는 `:<module>:test --tests '*Name'`.
- 로컬 실행: `cp .env.example .env` → 값 확인 → `set -a; source .env; set +a` → `docker compose up -d` → `./gradlew :api-rest:bootRun` (rest 8080/관리 8081, graphql 8090/8082, gRPC 9190/actuator 8083).
- 배치: `./gradlew :batch:bootRun --args='--spring.batch.job.name=memberPostStatsJob runDate=YYYY-MM-DD'` (종료 코드 0 성공 / 1 잘못된 파라미터·이미 완료 / 5 스텝 실패).
- 이미지: `./gradlew :<module>:jibDockerBuild` (`api-*`, `batch`). Dockerfile 금지.
- 환경 변수: `POSTGRES_PASSWORD`, `JWT_SECRET`(32바이트 이상, batch는 불필요), 선택 `DB_URL`·`DB_USERNAME`·`POSTGRES_HOST_PORT`. 시크릿 기본값·하드코딩 금지 (`changeme123`은 `.env.example` 샘플뿐).

## 규칙
- TDD: 실패하는 테스트 작성 → 실패 확인 → 최소 구현 → 리팩터링, 커밋은 Green에서만.
- Checkstyle: 메서드 ≤60줄, 파일 ≤350줄. getter/setter 직접 작성 금지(Lombok `@Getter`, 엔티티에 `@Setter` 금지, 전달 객체는 `record`).
- 패키지: `core/<member|post>/{entity,repository,service,dto}`, `security`·`aop`·`common`·`config`는 평평, 어댑터는 `controller`·`dto`·`config`·`error`.
- 앱 모듈은 `core`를 다른 패키지 트리에서 가져온다: `scanBasePackages` + `config/PersistenceConfig`(`@EntityScan`·`@EnableJpaRepositories`는 설정 클래스에 둔다, 앱 클래스에 두면 `@WebMvcTest` 슬라이스가 깨짐). `batch`는 자기 패키지만 스캔(JWT 불필요).
- 입력 검증은 `core`에 한 곳: `TextGuard`(NUL·제어문자), `InvalidRequestException`(→ 400 / BAD_REQUEST / INVALID_ARGUMENT), 비밀번호 72바이트, 검색어 200자. 새 문자열 입력은 SQL 전에 가드. 클라이언트 입력으로 500/INTERNAL이 나면 리뷰 Important.
- 회원 조회·수정·삭제는 본인 또는 ADMIN만, 게시글 `author`는 id+name만(이메일 노출 금지).
- 응답·로그에 예외 메시지·입력값·토큰 금지(고정 문구). AOP 로그에 인자 값 금지.
- 인증: `JwtDecoder` 빈은 `typ=access`만 허용. gRPC는 인터셉터 Context의 `AuthUser`를 쓰고(`CurrentUser` 금지) 공개 RPC는 Login/Refresh/Register뿐. Prometheus는 `health,prometheus`만 노출.

## 개발 흐름
요구사항이 바뀌면 코드보다 문서 먼저: `docs/requirements` → spec → plan을 커밋한 뒤 구현 (`superpowers:brainstorming` → `writing-plans` → `subagent-driven-development`). 자세한 내용은 `docs/ai-dlc/GUIDE.md`.

## 함정
- Boot 4: 스타터 이름이 바뀜(`spring-boot-starter-webmvc`, `-grpc-server`, 테스트 스타터 분리). Testcontainers 2.x 패키지는 `org.testcontainers.postgresql`. QueryDSL은 OpenFeign 포크 7.7(Hibernate 7.4).
- 테스트 DB는 JVM당 싱글턴 컨테이너: 커밋한 행은 `@AfterEach`에서 지운다(posts → members).
- 같은 디렉터리에서 Gradle 빌드를 동시에 두 개 돌리지 않는다. 호스트 5432가 쓰이면 `POSTGRES_HOST_PORT`와 `DB_URL`을 바꾼다.
