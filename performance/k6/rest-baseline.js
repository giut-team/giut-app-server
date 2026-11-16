import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const target = __ENV.TARGET || 'team-list';
const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const accessToken = __ENV.ACCESS_TOKEN;
const competitionId = __ENV.COMPETITION_ID;
const teamId = __ENV.TEAM_ID;
const chatRoomId = __ENV.CHAT_ROOM_ID;
const resultPath = __ENV.RESULT_PATH || `build/reports/performance/k6-${target}.md`;
const virtualUsers = Number(__ENV.K6_VUS || 10);
const warmupSeconds = Number(__ENV.K6_WARMUP_SECONDS || 5);
const measureSeconds = Number(__ENV.K6_MEASURE_SECONDS || 15);

const endpointDuration = new Trend('endpoint_duration', true);
const endpointFailed = new Rate('endpoint_failed');

export const options = {
  stages: [
    { duration: `${warmupSeconds}s`, target: virtualUsers },
    { duration: `${measureSeconds}s`, target: virtualUsers },
    { duration: '2s', target: 0 },
  ],
  thresholds: {
    checks: ['rate>0.99'],
    endpoint_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function targetUrl() {
  switch (target) {
    case 'team-list':
      return `${baseUrl}/api/teams?competitionId=${competitionId}&page=0&size=10`;
    case 'team-detail':
      return `${baseUrl}/api/teams/${teamId}`;
    case 'chat-room-list':
      return `${baseUrl}/api/chat-rooms`;
    case 'message-list':
      return `${baseUrl}/api/chat-rooms/${chatRoomId}/messages?size=30`;
    default:
      throw new Error(`지원하지 않는 TARGET입니다: ${target}`);
  }
}

export default function () {
  const response = http.get(targetUrl(), {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/json',
    },
    tags: { endpoint: target },
  });

  const success = check(response, {
    'HTTP status is 200': (result) => result.status === 200,
  });
  endpointDuration.add(response.timings.duration);
  endpointFailed.add(!success);
}

function value(data, metric, field, fallback = 0) {
  return data.metrics[metric]?.values?.[field] ?? fallback;
}

function fixed(number, digits = 3) {
  return Number(number).toFixed(digits);
}

export function handleSummary(data) {
  const requests = value(data, 'http_reqs', 'count');
  const requestRate = value(data, 'http_reqs', 'rate');
  const failureRate = value(data, 'endpoint_failed', 'rate') * 100;
  const checkRate = value(data, 'checks', 'rate') * 100;
  const duration = data.metrics.endpoint_duration?.values || {};

  const markdown = `# k6 REST API 기준 측정 — ${target}

## 측정 조건

- 기준 URL: \`${baseUrl}\`
- 가상 사용자: ${virtualUsers}명
- 증가 구간: ${warmupSeconds}초
- 고정 부하 구간: ${measureSeconds}초
- 감소 구간: 2초
- 총 설정 시간: ${warmupSeconds + measureSeconds + 2}초
- 요청 방식: think time 없이 연속 GET 요청

## 측정 결과

| 항목 | 결과 |
|---|---:|
| 전체 요청 | ${requests}건 |
| 처리량 | ${fixed(requestRate)} req/s |
| 평균 응답 시간 | ${fixed(duration.avg)} ms |
| 중앙값(p50) | ${fixed(duration.med)} ms |
| p90 | ${fixed(duration['p(90)'])} ms |
| p95 | ${fixed(duration['p(95)'])} ms |
| p99 | ${fixed(duration['p(99)'])} ms |
| 최대 응답 시간 | ${fixed(duration.max)} ms |
| 요청 실패율 | ${fixed(failureRate)}% |
| HTTP 200 검사 성공률 | ${fixed(checkRate)}% |

> 로컬 H2 기반 비교용 측정값입니다. 최종 포트폴리오 수치는 동일한 스크립트를 MySQL 성능 DB와 고정된 서버 사양에서 다시 실행해 기록합니다.
`;

  return {
    [resultPath]: markdown,
    stdout: `${markdown}\n`,
  };
}
