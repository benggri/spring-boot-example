# AI-DLC 가이드: Spec-driven + Subagent-driven 개발

이 문서는 이 저장소를 AI 코딩 에이전트와 함께 만든 방식을 정리한 실무 가이드다.
새 기능 추가나 새 프로젝트 시작 때 같은 흐름을 따라 하는 것이 목적이다.

## 1. 개요

AI-DLC(AI-Driven Development Life Cycle)는 AI가 개발 주기의 실행을 맡고 사람은 의도와 승인을 맡는 개발 방식이다.
이 문서의 용어 대응은 AWS의 AI-DLC 원문을 그대로 옮긴 것이 아니라 **이 저장소의 해석**이다. 원문의 정의가 필요하면 원문을 직접 확인한다.

이 저장소는 두 가지 규율을 겹쳐 썼다.

- **Spec-driven:** 코드보다 문서가 먼저다. 요구사항 → 설계(spec) → 계획 순으로 확정한 뒤에만 구현한다.
- **Subagent-driven:** 계획의 Task마다 새 서브에이전트가 구현하고, 별도 리뷰어가 검토한다. 컨텍스트가 섞이지 않고 리뷰가 독립적이다.

모듈은 `core`, `api-rest`, `api-graphql`, `api-grpc`, `batch` 다섯 개이며 전부 이 흐름으로 만들었다.
지켜야 할 기술 제약은 계획 문서의 Global Constraints에, 리뷰어가 집중할 위험은 Review Focus에 적혀 있다.

## 2. 단계 대응표

| AI-DLC 용어 | 이 저장소의 대응 |
|---|---|
| Intent | 요구사항 초안 `docs/requirements/2026-11-25/01/INITIAL-REQUIREMENTS.md` |
| Inception | 브레인스토밍(`/superpowers:brainstorming`) → spec(`docs/superpowers/specs/2026-11-25-spring-boot-example-design.md`) → 계획(`docs/superpowers/plans/2026-11-25-spring-boot-example.md`) |
| Unit | 계획의 Task 하나(Task 1~15) |
| Bolt | Task 하나의 구현 → 리뷰 → 수정 사이클 |
| Construction | `superpowers:subagent-driven-development` 실행 |
| Operations | Jib 컨테이너 이미지, Prometheus 모니터링(`monitoring/README.md`), 모듈별 README 런북, CLAUDE.md |

실제 진행 순서는 다음과 같았다.

1. 사람이 요구사항 초안을 쓴다.
2. 브레인스토밍이 짧은 Q&A로 초안을 보강한다. 목적(학습, 팀 참고 템플릿, 신규 프로젝트 가이드)을 확인하고, 구조(단일 앱 / 공유 core 멀티모듈 / 독립 앱)와 인증(자체 발급 JWT / OAuth2 리소스 서버 / 세션)을 사람이 골랐다. 결과는 멀티모듈과 자체 발급 JWT다.
3. spec을 쓰고 사람이 승인한다.
4. `superpowers:writing-plans`가 Global Constraints, Review Focus, Task 목록을 가진 계획을 만든다.
5. 사람이 실행 방식으로 Subagent-driven을 고른다.
6. Task별로 구현 → 리뷰 → 수정을 반복한다.
7. 전체 브랜치 최종 리뷰와 CLAUDE.md 갱신(`claude-md-management`)은 예정이며 이 문서 작성 시점에는 아직 하지 않았다.

## 3. Spec-driven 규칙

- spec이 최종 권위다. 계획은 spec을 실행 가능한 Task로 푼 것이며 논거를 담는다. 둘이 어긋나면 spec이 이긴다.
- 요구사항이 바뀌면 코드부터 고치지 않는다. 요구사항 → spec → 계획 순으로 문서를 먼저 고치고 커밋한 뒤 구현한다.
- 구현 중 발견한 사실이 설계를 바꾸면 같은 방식으로 문서에 되돌려 기록한다.

이 프로젝트에서 실제로 일어난 변경이다(모두 사용자 요청이 계기).

| 변경 | 계기 | 먼저 고친 문서 | 결과 |
|---|---|---|---|
| PostgreSQL 필수 기술 추가 | 사용자 요청 | 요구사항, spec, 계획 | Flyway 스키마, Testcontainers 테스트 |
| TDD 의무화 | 사용자 요청 | 요구사항, 계획 Global Constraints | Red→Green→Refactor, Green에서만 커밋 |
| Jib 필수(Dockerfile 없음) | 사용자 요청 | 요구사항, spec, 계획 | api 모듈은 Jib으로 이미지 빌드 |
| Spring AOP 추가 | 사용자 요청(진행 중) | 요구사항, spec, 계획(Task 10) | 로깅·감사 aspect |
| Prometheus 모니터링 추가 | 사용자 요청(진행 중) | 요구사항, spec, 계획(Task 11) | Actuator+Micrometer, 별도 관리 포트, `health,prometheus`만 노출 |
| Lombok 도입 | 사용자 요청 | 요구사항, 계획(Task 13) | getter/setter 생성, 엔티티에 `@Setter` 금지 |
| 패키지 구조 규칙 | 사용자 요청 | 계획(Task 14) | feature 우선, 단수형 `entity/repository/service/dto` 계층 폴더 |
| Spring Batch 예제 추가 | 사용자 요청 | 요구사항, 계획(Task 15) | `batch` 모듈 |
| 회원 조회를 본인 또는 ADMIN으로 제한 | 사용자 결정(spec 승인 후) | spec, 이후 구현 | 개인정보 보호 정책 반영 |
| AI-DLC 가이드와 OKF·graphify 비교 | 사용자 요청 | 요구사항, 계획(Task 12) | 이 문서 |

구현 중 발견해 설계가 바뀐 두 가지도 있다.

- 별도 spring-grpc starter가 Boot 4.2 BOM에서 해석되지 않아 Boot 자체의 `spring-boot-starter-grpc-server`를 썼다.
- Hibernate 7.4 호환을 위해 QueryDSL은 OpenFeign 포크 7.7을 택했다.

## 4. Subagent-driven 규칙

| 역할 | 하는 일 |
|---|---|
| 컨트롤러(메인 세션) | Task brief 파일을 만들고, 서브에이전트를 띄우고, 결과를 판단하고, ledger에 기록 |
| 구현 서브에이전트 | Task마다 새로 시작. brief 파일만 보고 TDD로 구현하고 커밋 |
| Task 리뷰어 | diff 패키지를 받아 스펙 준수와 코드 품질을 함께 검토 |
| fix loop | 지적 사항을 고치고 재리뷰. 최대 5회, 재리뷰는 수정 diff만 확인 |

- **Brief 파일:** 서브에이전트는 대화 기록을 모르므로 Task 전문, 제약, 검증 명령을 파일로 넘긴다.
- **모델 선택:** 보안·공개 표면처럼 위험이 큰 Task의 리뷰는 더 강한 모델로 돌렸다.
- **Ledger:** `.superpowers/sdd/<plan>/progress.md`에 진행 상황을 기록한다. 이 디렉터리는 git에서 제외된 로컬 작업 공간이며 커밋되지 않는다. brief, 보고서, 리뷰 패키지도 같은 곳에 있다.
- **`Ruling:`** 계획이 모호하거나 충돌할 때 컨트롤러가 내린 판단을 `Ruling N: 결정 - if wrong: 영향` 형식으로 ledger에 남긴다. 예: 서비스가 필요로 하는 `PasswordEncoder` 빈 정의가 뒤 Task에 있을 때의 처리, Jib 검증 범위.
- **Carry list:** 앞 Task의 리뷰에서 나온 보안 규칙을 이후 어댑터에도 빠짐없이 적용하도록 ledger에 적어 둔 목록이다. REST에서 찾은 규칙을 GraphQL과 gRPC의 dispatch에도 넘겨 같은 결함을 막았다.
- **최종 전체 리뷰:** 모든 Task가 끝나면 브랜치 전체를 한 번 더 리뷰한다. 이 문서 작성 시점에는 예정이다.

## 5. 사람의 게이트

| 게이트 | 이 프로젝트에서 실제로 일어난 일 |
|---|---|
| 요구사항 확정 | 사용자가 초안을 쓰고 검토 |
| spec 승인 | 사용자가 승인 |
| 계획 검토와 실행 방식 선택 | 계획 검토 후 Subagent-driven 선택 |
| 작업 공간 | worktree 대신 "branch only" 선택 |
| 이름·구조 결정 | 패키지 폴더명 `entity` 등 |
| 개인정보 결정 | 회원 조회는 본인 또는 ADMIN만 |
| 최종 리뷰와 병합 | 예정 |

에이전트가 멈추고 사람에게 물어야 하는 경우는 다음과 같다.

- 파괴적 작업(데이터·브랜치·파일 삭제, 강제 푸시)
- 보안 민감 작업(시크릿, 인증 정책, 권한 규칙 변경)
- 외부 부작용(원격 푸시, 외부 서비스 호출, 배포)
- 만들 대상을 바꾸는 결정, 계획 붕괴(계획대로 구현이 불가능한 경우)

이 프로젝트에서는 worktree 생성 동의와 설계를 바꾸는 결정에서 실제로 멈추고 물었다.

## 6. 품질 가드레일

| 가드레일 | 내용 |
|---|---|
| TDD | Red → Green → Refactor. 테스트와 구현을 Green 상태에서 한 커밋으로. 버그는 재현 테스트부터 |
| 크기 제한 | 함수 60줄, 파일 350줄. Checkstyle(`MethodLength`, `FileLength`)이 `./gradlew check`에서 강제 |
| 시크릿 | 하드코딩 금지. `JWT_SECRET`, DB 비밀번호는 환경 변수로만 주입. 이미지에도 넣지 않음 |
| 로깅 | 메서드 인자 값을 로그에 남기지 않음. 메트릭 태그는 class, method, outcome만 |
| 노출 최소화 | Actuator는 별도 포트에 `health`, `prometheus`만 노출 |

**가드레일이 편의 요청을 이긴 사례.** 사용자가 docker-compose에 기본 DB 비밀번호 리터럴(`changeme123`)을 넣어 달라고 요청했다.
조직의 시큐어 코딩 규칙(비밀번호 하드코딩 금지)에 어긋나므로 compose에는 넣지 않았다.
대신 `.env.example`에 샘플 값을 두고, compose는 `${POSTGRES_PASSWORD:?...}`로 값이 없으면 기동을 거부하게 했다.

**리뷰 루프에서 얻은 교훈.** 공개 표면을 검토한 보안 중심 리뷰가 "클라이언트가 유발할 수 있는 500" 결함을 여러 건 찾았다.

- UTF-8 72바이트를 넘는 비밀번호가 문자 수 기준 `@Size`를 통과해 BCrypt 한계에서 500
- GraphQL에서 빈 문자열 ID와 명시적 null `page`/`size`가 INTERNAL_ERROR
- 문자열의 NUL(`\u0000`)이 PostgreSQL 파라미터 바인딩을 깨뜨림
- `role` 클레임이 없는 서명된 JWT가 NullPointerException 유발

가능한 한 `core`에서 중앙 처리했다(`TextGuard`, `InvalidRequestException`). 그래서 REST, GraphQL, gRPC가 같은 규칙 하나를 공유한다.
또 하나의 교훈은 보안 규칙을 이후 어댑터의 dispatch에도 반드시 가져가야 한다는 점이다(carry list).
리뷰어 프롬프트에는 "리뷰해 줘" 대신 위와 같은 구체적 위험을 이름으로 적었다.

## 7. 따라 하기: 새 기능 추가 체크리스트

- [ ] **1. 요구사항 수정:** `docs/requirements/` 아래 요구사항 문서에 변경을 먼저 적는다(이 저장소는 날짜 폴더를 썼다).
- [ ] **2. 브레인스토밍:** `/superpowers:brainstorming`으로 목적과 선택지를 Q&A로 좁힌다. 선택은 사람이 한다.
- [ ] **3. spec 승인:** `docs/superpowers/specs/`의 설계 문서를 갱신하고 사람이 승인한다.
- [ ] **4. 계획:** `superpowers:writing-plans`로 Task, Global Constraints, Review Focus를 갱신한다. 새 보안 규칙은 Review Focus에 구체적으로 적는다.
- [ ] **5. 실행 준비:** 작업 공간을 정한다(이 저장소는 worktree 없이 브랜치만 썼다). 로컬 확인용으로 `cp .env.example .env`로 값을 채우고 `docker compose up -d`로 PostgreSQL을 띄운다. Testcontainers 테스트에는 Docker가 필요하다.
- [ ] **6. 구현:** `superpowers:subagent-driven-development`로 Task마다 구현 → 리뷰 → 수정. Task마다 `./gradlew check`를 통과시킨다(Checkstyle의 함수 60줄, 파일 350줄 포함).
- [ ] **7. 최종 리뷰:** 브랜치 전체를 리뷰하고, 사람이 병합을 결정한다.
- [ ] **8. CLAUDE.md 갱신:** `claude-md-management`로 이번에 배운 규칙과 명령을 CLAUDE.md에 반영한다.

각 단계에서 문서와 코드가 어긋나면 문서를 먼저 고친다. 모듈 README의 "왜 이렇게 했는가"도 함께 갱신한다.
모듈별 상세는 `api-rest/README.md`, `api-graphql/README.md`, `api-grpc/README.md`, `batch/README.md`, `monitoring/README.md`를 본다.

## 8. OKF와 graphify 비교·활용 (선택)

둘 다 필수가 아니다. 이 절은 지식이 쌓였을 때의 선택지를 소개한다.

**OKF(Open Knowledge Format).** Google Cloud가 공개한, 마크다운 파일과 YAML frontmatter로 AI 에이전트용 지식을 담는 번들 형식이다.

- 개념 하나가 파일 하나이고, 필수 필드는 `type` 하나뿐이다.
- `index.md`(점진적 공개)와 `log.md`는 선택이다. `type`이 없는 `.md`(README.md, AGENTS.md, CLAUDE.md 등)는 건너뛴다.
- 2026-11 기준 v0.3이고 1.0 이전이라 minor 버전에서도 깨지는 변경이 있을 수 있다. 도입 전 공식 저장소(github.com/open-knowledge-format/open-knowledge-format)에서 최신 버전을 확인한다.
- 에이전트 스킬 `okf-generate`(번들 작성·갱신·마이그레이션·검증)와 `okf-consume`(번들 기반 질의응답, 신뢰 등급·상태·최신성 보고)가 있다. Agent Skills 형식이며 설치법은 저장소 README의 `#agent-skills`를 본다.
- 한계: 폴더와 frontmatter라는 컨테이너 규격이지 지식의 의미까지 표준화하지는 않는다는 비판이 있다.

**이 프로젝트에서의 위치.** 기존 문서(CLAUDE.md, spec, 계획)는 OKF가 아니며 바꾸지 않는다.
도메인 개념과 결정 기록처럼 여러 서브에이전트가 반복해 참조할 지식이 커질 때만 별도 번들로 분리하는 선택지로 둔다. 만들 경우의 예시 레이아웃이다.

```text
docs/knowledge/index.md
docs/knowledge/domain/member.md
docs/knowledge/decisions/jwt-self-issued.md
```

frontmatter의 `type` 값은 공식 예시와 스펙에서 확인해 그대로 쓴다. 이 문서는 값을 임의로 만들지 않으므로 예시 값을 싣지 않는다.

**graphify와의 비교.** graphify는 이 환경에 설치된 스킬이다. 아래 내용은 사용법 설명에 근거하며, 추출 정확도와 갱신 비용은 직접 검증하지 않았다.

| 항목 | graphify | OKF |
|---|---|---|
| 성격 | 코드·문서·논문·이미지·영상에서 지식 그래프를 **자동 생성** | 사람·에이전트가 **작성하는** 지식 파일의 형식 |
| 산출물 | 대화형 HTML, GraphRAG용 JSON, `GRAPH_REPORT.md` | 마크다운 + frontmatter 번들 |
| 사용 | `/graphify query`, `path`, `explain` | `okf-generate`, `okf-consume` 스킬 |
| 옵션 | `--update`(증분), `--watch`, `--mcp`(에이전트 접근), `--wiki`, `--obsidian` | `index.md`, `log.md`(선택) |
| 신뢰 표기 | 엣지에 EXTRACTED / INFERRED / AMBIGUOUS | 신뢰 등급·상태·최신성(`okf-consume`이 보고) |

두 도구는 대체 관계가 아니라 보완 관계다. 코드 구조와 관계 탐색은 graphify, 도메인 용어와 설계 결정처럼 선별한 지식의 저장·공유는 OKF가 맞다.

**이 프로젝트 권고.** 코드가 적은 초기에는 둘 다 불필요하다. 모듈이 쌓인 뒤에는 graphify가 더 유리하다.
서비스 → 어댑터 → 보안 관계를 질의할 수 있고, 서브에이전트 컨텍스트를 줄이며, 별도 문서를 유지하지 않아도 되기 때문이다.
선택 사항으로, 구현이 끝난 뒤 `/graphify`를 한 번 실행해 graphify-out/ 디렉터리에 그래프를 만들어 볼 수 있다. 필수 단계는 아니다.
