# Spring Boot 예제 프로젝트 설계 (개선된 요구사항)

## Context / 의도
- 용도: 개발자 학습용, 팀 참고 템플릿, 신규 프로젝트 개발 가이드 (세 가지 모두).
- 따라서 "바로 돌려 볼 수 있음", "읽기 쉬움", "필요한 모듈만 가져가기 쉬움"이 핵심 가치.
- 원본: `docs/requirements/2026-11-25/01/INITIAL-REQUIREMENTS.md` (저장소는 사실상 빈 상태, JDK 21 확인됨).

## Goals
- Spring Boot 4.2.0, Java 21, Gradle(Groovy) 멀티모듈.
- 필수 기술: Spring Security, REST/GraphQL/gRPC CRUD, JPA + QueryDSL, Swagger(OpenAPI), PostgreSQL.
- 세 프로토콜이 **동일 도메인·동일 서비스 계층**을 공유해 프로토콜 차이만 드러나게 한다.

## Non-goals / Anti-goals
- Non-goal: 외부 IdP(Keycloak 등), 세션 로그인, 마이크로서비스 인프라(메시지 브로커, 게이트웨이), 프론트엔드.
- Anti-goal: 도메인이 복잡해서 기술이 묻히는 것, 프로토콜 모듈마다 JPA 코드가 중복되는 것.

## Approaches (검토 결과)
| 안 | 내용 | 결과 |
|---|---|---|
| A | 단일 앱에 3 프로토콜 | 포트·설정이 섞이고 분리 어려움 → 탈락 |
| **B** | **멀티모듈: `core` + `api-rest`/`api-graphql`/`api-grpc`** | **사용자 선택** (도메인 중복 없음, 모듈 경계 시연) |
| C | 독립 앱 3개 | JPA 코드 3벌 중복 → 탈락 |

인증: **자체 발급 JWT 선택** (대안: OAuth2 Resource Server+IdP는 실행 부담, 세션은 gRPC와 부적합).

## 설계 (사용자가 정한 것)
- 모듈: `core`, `api-rest`, `api-graphql`, `api-grpc`. 의존은 `api-*` → `core` 단방향.
- 서비스는 `core`에, 컨트롤러/리졸버/gRPC 서비스는 얇은 어댑터.
- 인증: 자체 발급 JWT(access/refresh), 역할 `USER`/`ADMIN`. 같은 토큰을 REST·GraphQL 헤더, gRPC 메타데이터에 사용.
- 개발 가이드: 함수 60줄 이하, 클래스 350줄 이하, 한 함수 한 역할, 사람이 유지보수하는 관점.
- TDD(사용자 요구): Red(실패 테스트 먼저, 실패 확인) → Green(최소 구현) → Refactor. 커밋은 Green에서만. 테스트 계층은 단위, 슬라이스, 통합(Testcontainers, gRPC in-process). 버그 수정도 재현 테스트부터. 구현 시 `superpowers:test-driven-development` 스킬을 따른다.
- 시큐어 코딩: 시크릿 하드코딩 금지(환경 변수), SQL 인젝션·XSS 방어.
- AOP(사용자 추가 요구): `core`에 `ServiceLoggingAspect`(서비스 호출 로깅 + 실행 시간, 임계값 `app.aop.slow-threshold-ms` 초과 시 WARN)와 `AuditAspect`(`@Audited("동작명")` 메서드의 행위자·결과 로깅). 인자 값은 기록하지 않는다(시크릿 유출 방지). 대상 구성은 제 판단.
- Prometheus(사용자 추가 요구): 각 api 앱에 Actuator + `micrometer-registry-prometheus`. management 포트 분리, 노출은 `health`,`prometheus`만, 전용 `SecurityFilterChain`(`EndpointRequest`)으로 두 엔드포인트만 permitAll. `ServiceLoggingAspect`가 `app.service.invocation` Timer(태그: class, method, outcome — 저카디널리티)를 기록. docker-compose `monitoring` 프로필로 Prometheus 제공, Grafana는 범위 밖(제 판단). management 포트는 외부 비공개 전제를 README에 명시.
- AI-DLC 가이드(사용자 추가 요구): `docs/ai-dlc/GUIDE.md`. 개발 방식은 Spec-driven + Subagent-driven(사용자 선호). AWS AI-DLC의 용어(Intent, Unit, Bolt, Inception/Construction/Operations)를 이 프로젝트의 산출물에 대응시킨 해석은 제 판단이며, 가이드에 "이 저장소의 해석"임을 명시한다.
- OKF(사용자 요청, 유용할 경우): 확인 결과 Google Cloud가 공개한 마크다운+YAML frontmatter 기반 지식 번들 형식(현재 v0.3, 1.0 이전이라 minor에서도 호환 깨짐 가능). 이 저장소에서는 **선택 사항**으로 가이드에 소개만 한다. 기존 CLAUDE.md·spec·계획을 OKF로 바꾸지 않고, 번들(`docs/knowledge/`)도 만들지 않는다(YAGNI, 제 판단).
- graphify 비교(사용자 요청): 가이드 OKF 절에서 graphify(설치된 스킬, 코드·문서 → 지식 그래프 자동 생성·질의)와 비교한다. 보완 관계로 서술하고, 구현 후 `/graphify` 실행은 선택 안내로만 둔다(제 판단). 정확도·비용은 미검증으로 명시.
- 컨테이너 이미지(사용자 요구): Jib Gradle 플러그인으로 빌드, Dockerfile 없음. 세 api 앱이 대상이며 시크릿은 이미지에 포함하지 않는다. 베이스 이미지(`eclipse-temurin:21-jre`)는 제 판단.
- AI 스킬: superpowers(brainstorming, writing-plans, subagent-driven-development), claude-md-management(완료 후 CLAUDE.md 갱신), `.claude/skills/java-springboot`.

## 제 판단 (사용자가 확인·수정 가능)
- 도메인: `Member` 1:N `Post`(게시글). 단순하면서 QueryDSL 동적 검색·페이징을 보여 주기에 충분.
- DB: PostgreSQL 필수(사용자 요구). 실행은 docker-compose로 제공하고, 테스트는 Testcontainers(PostgreSQL)를 쓴다(제 판단).
- 테스트: 모듈별 슬라이스 + 통합 테스트(테스트도 예제). 보안 테스트 포함.
- 문서: 루트 README + 모듈별 README("왜 이렇게 했는가"), 완료 후 CLAUDE.md 갱신.
- 패키지 루트: `com.benggri.example` (임의 결정).
- 라이브러리 선택(빌더 판단): Swagger=springdoc-openapi, GraphQL=spring-graphql, gRPC=Spring Boot 내장 `spring-boot-starter-grpc-server`(Task 1 검증: 별도 spring-grpc 스타터는 Boot 4.2 BOM에서 해석되지 않음), QueryDSL=OpenFeign 포크 또는 Boot 4 호환 버전.
- Swagger/QueryDSL은 REST 모듈 중심, 인증은 세 모듈 모두 적용.

## 빌더에게 남기는 것 / 리스크
- **Boot 4.2.0 및 각 라이브러리(springdoc, QueryDSL, spring-grpc)의 호환 버전은 구현 첫 단계에서 실제 빌드로 검증**한다. 미호환 시 사용자에게 알리고 조정.
- 모듈 간 JWT 검증 코드 공유 위치(`core`의 security 패키지 vs 별도 `security` 모듈)는 빌더가 결정.
- 구현 순서: 골격/빌드 → core(도메인·JPA·QueryDSL) → 인증 → rest(+Swagger) → graphql → grpc → 문서 → CLAUDE.md.

## 검증
- `./gradlew build` 전 모듈 통과, 각 api 모듈 기동 후 로그인→토큰→CRUD 호출 시나리오 확인(REST, GraphQL, gRPC), Swagger UI 접근, 60/350줄 가이드 위반 점검.

## 메모
- 빌더 검토용 서브에이전트는 아직 돌리지 않았고, 위 내용은 제가 빌더 관점에서 직접 점검했습니다.
