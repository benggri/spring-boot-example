# batch

Spring Batch 6(Spring Boot 4.2) 예제 모듈입니다. 하루치 게시글 수를 회원별로 집계해 `member_post_stats` 에 저장하고, 보존 기간이 지난 통계를 지우는 잡 `memberPostStatsJob` 한 개를 실행하고 종료하는 단발성 프로세스입니다. 웹 서버는 띄우지 않습니다(`spring.main.web-application-type: none`).

## 왜 이렇게 했는가

- **chunk + tasklet 분리**: 집계(`aggregatePostCountsStep`)는 읽기 → 변환 → 쓰기가 나뉘고 행 수가 늘 수 있으므로 chunk 지향 스텝으로 만들어 chunk 단위로 커밋합니다. 오래된 통계 삭제(`purgeOldStatsStep`)는 SQL 한 문장이므로 tasklet 으로 충분합니다.
- **UTC 하루 구간**: 대상 구간은 `[runDate 00:00Z, runDate+1일 00:00Z)` 이고 `created_at >= ? AND created_at < ?` 로 바인딩합니다. JVM/서버 시간대에 따라 같은 `runDate` 의 결과가 달라지지 않게 하려는 것이며, 반열림 구간이라 자정 경계의 글이 두 날짜에 중복되거나 빠지지 않습니다. 모든 SQL 값은 파라미터 바인딩이고 문자열 연결은 없습니다. reader 는 `member_id` 순으로 정렬한 JDBC cursor reader 입니다.
- **멱등 upsert**: writer 는 `INSERT ... ON CONFLICT (member_id, stat_date) DO UPDATE SET post_count = EXCLUDED.post_count` 입니다. 같은 `runDate` 를 다시 처리해도 행이 늘지 않고 값만 최신으로 갱신됩니다. processor 는 게시글이 0건이면 `null` 을 반환해 건너뜁니다.
- **보존 기간**: `stat_date < runDate - retentionDays` 만 삭제합니다. 정확히 `runDate - retentionDays` 인 날은 유지됩니다.
- **스캔 범위 제한**: `@SpringBootApplication(scanBasePackages = "com.benggri.example.batch")` + `config/PersistenceConfig`(`@EntityScan`/`@EnableJpaRepositories` 는 core) 로 core 의 엔티티와 Spring Data 저장소만 가져옵니다. core 의 서비스·AOP·`JwtConfig` 는 스캔하지 않으므로 배치 실행에 `JWT_SECRET` 이 필요 없습니다(`context_startsWithoutJwtSecret` 테스트). web/security/actuator/graphql/grpc 의존성도 없습니다.
- **메타데이터 스키마**: `spring.batch.jdbc.initialize-schema: always` 로 batch 앱이 시작할 때 `BATCH_*` 테이블을 만듭니다. Flyway 마이그레이션은 core 에 있고 모든 앱이 공유하므로, 배치 전용 테이블을 core 에 넣지 않고 batch 앱에서만 초기화합니다(api-* 앱은 batch 의존성이 없어 테이블을 만들지 않습니다). 새 DB 에서 첫 실행과 두 번째 실행 모두 오류 없이 시작함을 확인했습니다(Flyway 가 만든 테이블과 같은 스키마에 공존).
- **FK 와 회원 삭제**: V2 마이그레이션의 `member_post_stats.member_id` 는 `REFERENCES members(id) ON DELETE CASCADE` 입니다. 회원을 삭제하면 통계도 함께 지워지고, 통계 행 때문에 회원 삭제가 실패하지 않습니다(core 의 `delete_memberWithStats_succeeds`). 통계는 JDBC 로만 읽고 쓰므로 JPA 엔티티는 만들지 않았습니다.
- **Prometheus/actuator 제외**: 잡이 끝나면 프로세스가 종료되는 단발성 작업이라 스크레이프할 대상이 없어 의도적으로 넣지 않았습니다. 결과는 종료 코드와 `BATCH_*` 메타데이터 테이블로 확인합니다.
- **재시작(관찰·테스트로 확인)**: skip/retry 정책은 두지 않았습니다. 스텝이 실패하면 잡 실행은 `FAILED` 로 남고(`job_stepFailureLeavesJobRestartable`: 쓰기 대상 테이블이 없는 상태로 `aggregatePostCountsStep` 실패를 유도), 원인을 고친 뒤 **같은 식별 파라미터로 그대로 다시 시작**하면 같은 JobInstance 의 새 실행이 만들어져 `COMPLETED` 가 됩니다(별도 restart 호출 불필요, `JobOperator.start` 가 처리). CLI 로도 같은 인자(`runDate=...`)로 다시 실행하면 실패한 인스턴스를 다시 실행하는 것을 확인했습니다. 이미 `COMPLETED` 인 인스턴스를 같은 파라미터로 다시 시작하면 `JobInstanceAlreadyCompleteException` 으로 거부됩니다. 같은 날짜를 일부러 다시 집계하려면 다른 식별 파라미터를 추가합니다(예: `runDate=2026-11-25 rerun=2`).
- **알려진 한계**: (1) reader 는 그날 게시글이 있는 회원만 반환하므로, 첫 실행 뒤 그날의 게시글이 모두 삭제된 회원의 통계 행은 재실행해도 지워지지 않고 이전 `post_count` 를 유지합니다. (2) 충돌 시 `post_count` 만 덮어쓰며 `created_at` 은 처음 insert 한 값이 유지됩니다.

## 파라미터와 설정

| 이름 | 기본값 | 설명 |
|---|---|---|
| `runDate` (잡 파라미터) | 필수 | ISO `yyyy-MM-dd`. 없거나 형식 오류면 잡 시작 자체를 거부(스텝 미실행, 쓰기 없음) |
| `app.batch.chunk-size` | 100 | chunk 크기, 1 이상 |
| `app.batch.retention-days` | 90 | 통계 보존 일수, 1 이상 |

## 환경 변수

| 이름 | 필수 | 설명 |
|---|---|---|
| `POSTGRES_PASSWORD` | 예 | DB 비밀번호 (기본값 없음) |
| `DB_URL` | 아니오 | 기본 `jdbc:postgresql://localhost:5432/example` |
| `DB_USERNAME` | 아니오 | 기본 `example` |

## 실행

```bash
cp .env.example .env        # 값을 채웁니다
docker compose up -d        # PostgreSQL
set -a; source .env; set +a # POSTGRES_PASSWORD 를 셸 환경에 export
./gradlew :batch:bootRun --args='--spring.batch.job.name=memberPostStatsJob runDate=2026-11-25'
```

jar 로는 `java -jar batch/build/libs/batch-0.0.1-SNAPSHOT.jar --spring.batch.job.name=memberPostStatsJob runDate=2026-11-25` 입니다.

**종료 코드(실제 `java -jar` 실행으로 관찰)**: 성공 `0`. `runDate` 형식 오류(`InvalidJobParametersException`)와 이미 완료된 인스턴스 재실행(`JobInstanceAlreadyCompleteException`)은 시작 단계 예외라 `1`. 스텝이 실패한 잡(빈 DB 에서 reader 가 `posts` 를 못 찾는 경우)은 `5` 였습니다. `main` 이 `System.exit(SpringApplication.exit(...))` 를 쓰기 때문입니다.

## 테스트

`./gradlew :batch:test` — 실제 PostgreSQL(Testcontainers). 테스트 JVM 은 비 UTC 기본 시간대(`user.timezone=Asia/Seoul`, `batch/build.gradle`)로 실행해 UTC 하루 구간이 JVM 시간대와 무관함을 확인하며, reader/writer/purge 는 `OffsetDateTime`/`LocalDate` 를 `setObject` 로 바인딩합니다. 테스트에서는 `spring.batch.job.enabled=false` 로 컨텍스트 시작 시 잡이 자동 실행되지 않게 하고 `JobOperatorTestUtils` 로 직접 실행합니다.
