# Spring Boot 예제 프로젝트

## 목적

- Spring Boot 4.2.0 (Java 21 + Gradle Groovy) 기반 예제 프로젝트를 개발한다.
- 실무에서 많이 쓰는 기술의 예제를 제공한다.
- 사용 대상과 용도
  - 개발자 학습용
  - 팀 참고 템플릿
  - 신규 프로젝트 개발 가이드
- 핵심 가치: 바로 실행해 볼 수 있음, 읽기 쉬움, 필요한 모듈만 가져가기 쉬움.

## 필수 기술

- Spring Security (자체 발급 JWT, 역할 `USER`/`ADMIN`)
- REST API, GraphQL, gRPC: 각각 CRUD 예제
- JPA + QueryDSL (동적 검색, 페이징)
- Swagger (OpenAPI)
- PostgreSQL
- Docker 이미지 빌드는 Jib(Gradle 플러그인)로 한다. Dockerfile은 작성하지 않는다.
  - 대상: `api-rest`, `api-graphql`, `api-grpc` 각 앱
  - 시크릿(`JWT_SECRET`, DB 비밀번호)은 이미지에 넣지 않고 실행 시 환경 변수로 주입한다.

## 범위

- 목표
  - 세 프로토콜이 같은 도메인과 같은 서비스 계층을 공유해 프로토콜 간 차이만 드러나게 한다.
  - 같은 JWT를 REST·GraphQL 헤더와 gRPC 메타데이터에 사용한다.
- 비목표: 외부 IdP(Keycloak 등), 세션 로그인, 메시지 브로커, API 게이트웨이, 프론트엔드.
- 피할 것: 기술이 묻힐 만큼 복잡한 도메인, 모듈마다 반복되는 JPA 코드.

## 구조 (확정)

- Gradle 멀티모듈. 의존 방향은 `api-*` → `core` 단방향이다.
  - `core`: 엔티티, JPA, QueryDSL, 서비스, 보안 공통
  - `api-rest`: REST API + Swagger
  - `api-graphql`: GraphQL
  - `api-grpc`: gRPC
- 컨트롤러, 리졸버, gRPC 서비스는 서비스 계층을 호출하는 얇은 어댑터로 둔다.
- 도메인: `Member` 1:N `Post`.

## 개발 가이드

- 하나의 함수는 하나의 역할
- 하나의 함수는 60라인 이하, 초과 시 분리
- 하나의 클래스는 350라인 이하, 초과 시 분리
- 사람이 유지보수하는 관점에서 개발
- 모듈별 README에 "왜 이렇게 했는가"를 기록
- 테스트도 예제로 취급 (슬라이스 테스트 + 통합 테스트)
- TDD 필수
  - Red: 실패하는 테스트를 먼저 작성하고, 실패하는 것을 직접 확인한다.
  - Green: 테스트를 통과시키는 최소한의 구현만 작성한다.
  - Refactor: 테스트가 통과한 상태에서만 정리하고, 60/350라인 가이드를 이때 맞춘다.
  - 커밋은 Green 상태에서만 한다. 테스트 없는 기능 코드는 허용하지 않는다.
  - 테스트 계층: 단위(서비스, JWT), 슬라이스(`@DataJpaTest`, `@WebMvcTest`, GraphQL 테스터), 통합(Testcontainers PostgreSQL, gRPC in-process).
  - 버그 수정도 재현하는 실패 테스트부터 작성한다.
- 시큐어 코딩: API 키·비밀번호·JWT 시크릿 하드코딩 금지(환경 변수 사용), SQL 인젝션·XSS 방어

## AI 스킬

- superpowers (brainstorming, writing-plans, subagent-driven-development, test-driven-development)
- claude-md-management (개발 완료 후 CLAUDE.md 업데이트 및 개선)
- `.claude/skills/java-springboot`

## 열린 위험

- Spring Boot 4.2.0과 springdoc-openapi, QueryDSL, spring-grpc의 호환 버전은 구현 첫 단계에서 실제 빌드로 검증한다.

## 관련 문서

- 설계: `docs/superpowers/specs/2026-11-25-spring-boot-example-design.md`
