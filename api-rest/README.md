# api-rest

REST + Spring Security(JWT) + Swagger 어댑터 모듈입니다. 비즈니스 규칙(소유권 검사, 중복 검사 등)은 모두 `core` 서비스에 있고, 이 모듈은 HTTP 변환·검증·인증 연결만 담당합니다.

## 왜 이렇게 했는가

- **Stateless + CSRF 비활성**: 세션과 쿠키를 쓰지 않고 매 요청의 `Authorization: Bearer` 헤더로만 인증합니다. 브라우저가 자동으로 붙여 보내는 자격 증명이 없으므로 CSRF 공격 경로가 없고, 그래서 CSRF 보호를 끕니다. (쿠키 기반 인증으로 바꾸면 반드시 다시 켜야 합니다.)
- **토큰 흐름**: `POST /auth/login` 으로 access(15분)/refresh(7일) 토큰 쌍을 받고, API 호출에는 access 토큰만 씁니다. access 가 만료되면 `POST /auth/refresh` 로 새 쌍을 받습니다. 두 토큰은 `typ` 클레임으로 구분되어, refresh 토큰을 Bearer 로 쓰거나 access 토큰으로 refresh 하면 401 입니다. refresh 는 회전/폐기 목록이 없는 stateless 방식이며, 탈퇴한 회원의 refresh 는 401 입니다.
- **오류 형식**: 모든 오류는 `application/problem+json`(ProblemDetail)입니다. 401/403/404/409 등의 `detail` 은 고정된 일반 문구이며 예외 메시지·토큰·거부된 입력값(특히 비밀번호)을 절대 담지 않습니다. 검증 실패(400)는 필드 이름과 제약 메시지만 알려 줍니다. 예기치 못한 오류(500)는 서버 로그에만 상세를 남깁니다.
- **JWT 검증**: `JwtDecoder` 는 `core` 의 단일 빈(access 전용, sub/role 필수)을 사용하고, `role` 클레임을 `ROLE_<role>` 권한으로 변환합니다.
- **인증 불필요 경로**: `POST /auth/login`, `POST /auth/refresh`, `POST /api/members`(가입), `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. 나머지는 모두 인증이 필요합니다.
- 회원을 삭제하면 그 회원의 게시글도 함께 삭제됩니다.

## 엔드포인트

| Method | Path | 설명 |
|---|---|---|
| POST | `/auth/login` | 로그인, 토큰 쌍 발급 |
| POST | `/auth/refresh` | 토큰 쌍 재발급 |
| POST | `/api/members` | 회원 가입 (201 + Location) |
| GET / PUT / DELETE | `/api/members/{id}` | 조회 / 이름 변경 / 삭제 (204). 모두 본인 또는 ADMIN 만 가능 |
| GET | `/api/posts?keyword&memberId&page&size` | 검색 (size 100 초과는 100 으로 제한) |
| POST | `/api/posts` | 게시글 작성 (201 + Location) |
| GET / PUT / DELETE | `/api/posts/{id}` | 조회 / 수정 / 삭제 (204) |

목록 응답: `{ "items": [...], "page": 0, "size": 20, "total": 0 }`. 문서는 `/swagger-ui.html`.

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
./gradlew :api-rest:bootRun
```

그 다음 http://localhost:8080/swagger-ui.html 에 접속합니다.

## 입력 정제 (NUL 문자)

JSON 은 `\u0000` 을 허용하지만 PostgreSQL 은 NUL 문자를 저장/조회할 수 없어 드라이버 예외(500)가 됩니다. `core` 의 `TextGuard` 가 SQL 로 가는 모든 클라이언트 문자열(이메일, 이름, 제목, 내용, 검색 키워드)에서 NUL 과 탭/개행을 제외한 C0 제어문자를 저장소 호출 전에 거부하며 HTTP 400 `application/problem+json` 입니다. 검색 키워드는 200자로 제한되고 빈 비밀번호도 `core` 가 거부합니다.
