# 모니터링 (Prometheus)

세 앱이 `/actuator/prometheus` 로 메트릭을 노출하고, docker compose 의 `monitoring` 프로필이 Prometheus 를 띄웁니다. Grafana 와 `batch` 모듈은 이 작업의 범위가 아닙니다.

## 포트

| 앱 | 앱 포트 | actuator(스크랩) 포트 |
|---|---|---|
| api-rest | 8080 | 8081 (관리 포트) |
| api-graphql | 8090 | 8082 (관리 포트) |
| api-grpc | gRPC 9190 | 8083 (HTTP, 별도 관리 포트 없음) |
| Prometheus | 127.0.0.1:9090 | - |

노출되는 actuator 엔드포인트는 `health`, `prometheus` 뿐이고 health 는 상세 정보를 숨깁니다. `/actuator` 링크(discovery) 페이지도 꺼져 있습니다. 관리 포트는 인증이 없으므로 외부에 공개하지 마세요. Prometheus 포트도 인증이 없어 루프백(`127.0.0.1`)에만 게시합니다.

## 실행

```bash
# 앱 3개를 호스트에서 실행한 뒤 (DB_URL, POSTGRES_PASSWORD, JWT_SECRET 필요)
export POSTGRES_PASSWORD=<값>   # compose 파일 검증에도 필요
docker compose --profile monitoring up -d prometheus
# 준비 확인: curl http://127.0.0.1:9090/-/ready , UI: http://127.0.0.1:9090/targets
docker compose --profile monitoring down
```

컨테이너는 `host.docker.internal`(`extra_hosts: host-gateway`)로 호스트의 앱을 scrape 합니다 (`monitoring/prometheus.yml`). 이를 위해 앱의 관리/actuator 포트는 루프백이 아닌 주소로 바인딩되어야 하므로 `management.server.address`(api-grpc 는 `server.address`)를 제한하지 않았습니다(문서화된 트레이드오프, 인증 없는 포트이므로 외부 공개 금지). 앱이 꺼져 있으면 target 이 DOWN 으로 보이는 것이 정상입니다.

## 커스텀 메트릭

`ServiceLoggingAspect` 가 서비스 호출마다 Timer `app.service.invocation` 을 기록합니다 (Prometheus 이름 `app_service_invocation_seconds_count|sum|max`). 태그는 `class`, `method`, `outcome`(`success`/`failure`) 세 개뿐이며 요청 데이터는 태그가 되지 않습니다.

## PromQL 예시

`_bucket` 시리즈(예: `histogram_quantile` 로 p95)는 기본으로 노출되지 않습니다. 필요하면 `management.metrics.distribution.percentiles-histogram.http.server.requests=true` 를 켜야 하므로, 아래 예시는 지금 설정에서 동작하는 `_count`/`_sum` 기반 쿼리만 씁니다.

```promql
# 서비스 메서드별 초당 호출 수
sum by (class, method) (rate(app_service_invocation_seconds_count[5m]))
# 실패 비율
sum(rate(app_service_invocation_seconds_count{outcome="failure"}[5m])) / sum(rate(app_service_invocation_seconds_count[5m]))
# 메서드별 평균 소요 시간(초)
sum by (method) (rate(app_service_invocation_seconds_sum[5m])) / sum by (method) (rate(app_service_invocation_seconds_count[5m]))
# HTTP 서버 요청 평균 소요 시간(초), uri 별 (api-rest, api-graphql)
sum by (uri) (rate(http_server_requests_seconds_sum[5m])) / sum by (uri) (rate(http_server_requests_seconds_count[5m]))
# JVM 힙 사용량 / 인스턴스 상태
sum by (instance) (jvm_memory_used_bytes{area="heap"})
up{job="example-apps"}
```
