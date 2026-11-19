// 공모전 API 단계별 부하 테스트 (실제 MySQL 성능 DB 대상)
//
// 실행 예:
//   JWT_SECRET=<서버와 같은 값> k6 run performance/k6/competition-load.js
//
// 서버와 같은 JWT_SECRET으로 테스트 사용자(학교 인증 완료 STUDENT) 토큰을 직접 만들어 쓴다.
// 사용자·공모전 id 범위는 시드에 맞춰 USER_ID_MIN/MAX, COMPETITION_ID_MIN/MAX로 바꾼다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const jwtSecret = __ENV.JWT_SECRET;
const userIdMin = Number(__ENV.USER_ID_MIN || 5);
const userIdMax = Number(__ENV.USER_ID_MAX || 5004);
const competitionIdMin = Number(__ENV.COMPETITION_ID_MIN || 2);
const competitionIdMax = Number(__ENV.COMPETITION_ID_MAX || 50001);
const tokenCount = Number(__ENV.TOKEN_COUNT || 1000);
const thinkMaxSeconds = Number(__ENV.THINK_MAX_SECONDS || 0.5);
const stageHoldSeconds = Number(__ENV.STAGE_HOLD_SECONDS || 60);
const rampSeconds = Number(__ENV.RAMP_SECONDS || 30);
const levels = (__ENV.LEVELS || '10,50,100,200').split(',').map(Number);
const resultPath = __ENV.RESULT_PATH || 'build/reports/performance/k6-competition-load.md';

// 단계: (증가 rampSeconds → 유지 stageHoldSeconds) × 각 단계, 마지막에 감소
const stages = [];
const stagePeriods = []; // 각 단계가 차지하는 시간 구간(초)
let cursor = 0;
for (const level of levels) {
  stages.push({ duration: `${rampSeconds}s`, target: level });
  stages.push({ duration: `${stageHoldSeconds}s`, target: level });
  stagePeriods.push({ label: stageLabel(level), level, from: cursor, holdFrom: cursor + rampSeconds, to: cursor + rampSeconds + stageHoldSeconds });
  cursor += rampSeconds + stageHoldSeconds;
}
stages.push({ duration: `${rampSeconds}s`, target: 0 });

function stageLabel(level) {
  return `vu${String(level).padStart(3, '0')}`;
}

const endpoints = [
  // [이름, 비중(%)]
  ['list_latest', 25], ['list_deadline', 10], ['list_views', 8], ['list_scraps', 7],
  ['list_keyword', 8], ['list_category', 7], ['top5', 12], ['closing_soon', 8],
  ['detail', 10], ['scrap_add', 3], ['scrap_remove', 2],
];
const totalWeight = endpoints.reduce((sum, [, weight]) => sum + weight, 0);

const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};
for (const [name] of endpoints) {
  thresholds[`http_req_duration{name:${name}}`] = ['p(95)>=0'];
  thresholds[`http_req_failed{name:${name}}`] = ['rate>=0'];
}
for (const period of stagePeriods) {
  // 증가 구간은 빼고 유지 구간만 단계 결과로 센다.
  thresholds[`http_req_duration{stage:${period.label}}`] = ['p(95)>=0'];
  thresholds[`http_req_failed{stage:${period.label}}`] = ['rate>=0'];
  thresholds[`http_reqs{stage:${period.label}}`] = ['count>=0'];
}

export const options = {
  scenarios: {
    competition_mix: { executor: 'ramping-vus', startVUs: 0, stages, gracefulRampDown: '10s' },
  },
  thresholds,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const keywords = ['AI', '데이터', '디자인', '마케팅', '앱', '게임', '영상', '아이디어'];
const categories = ['AI_DATA', 'WEB_MOBILE_IT', 'DESIGN_CHARACTER_WEBTOON', 'GAME_SOFTWARE', 'PLANNING_IDEA', 'VIDEO_UCC_PHOTO'];

function base64url(value) {
  return encoding.b64encode(value, 'rawurl');
}

function makeToken(userId, keyBytes, nowSeconds) {
  const header = base64url(JSON.stringify({ alg: 'HS256' }));
  const payload = base64url(JSON.stringify({
    sub: String(userId),
    email: `k6-user-${String(userId - userIdMin).padStart(5, '0')}@giut.local`,
    role: 'STUDENT',
    type: 'access',
    tokenVersion: 0,
    iat: nowSeconds,
    exp: nowSeconds + 6 * 3600,
  }));
  const signature = crypto.hmac('sha256', keyBytes, `${header}.${payload}`, 'base64rawurl');
  return `${header}.${payload}.${signature}`;
}

export function setup() {
  if (!jwtSecret) throw new Error('JWT_SECRET 환경변수가 필요합니다(서버와 같은 값).');
  const keyBytes = encoding.b64decode(jwtSecret);
  const now = Math.floor(Date.now() / 1000);
  const tokens = [];
  const span = userIdMax - userIdMin + 1;
  for (let i = 0; i < tokenCount; i++) {
    tokens.push({ userId: userIdMin + Math.floor((i * span) / tokenCount), token: makeToken(userIdMin + Math.floor((i * span) / tokenCount), keyBytes, now) });
  }
  // 토큰이 실제로 통과하는지 먼저 확인한다.
  const probe = http.get(`${baseUrl}/api/competitions/top5`, { headers: { Authorization: `Bearer ${tokens[0].token}` } });
  if (probe.status !== 200) throw new Error(`토큰 확인 실패: HTTP ${probe.status} ${probe.body}`);
  return { tokens };
}

function pickEndpoint() {
  let roll = Math.random() * totalWeight;
  for (const [name, weight] of endpoints) {
    roll -= weight;
    if (roll < 0) return name;
  }
  return endpoints[0][0];
}

// 시드 규칙: 공모전 n번째(id - COMPETITION_ID_MIN)가 n % 20 == 0이면 DRAFT, == 1이면 마감 지남.
function pickOpenCompetitionId(popular) {
  for (;;) {
    const span = competitionIdMax - competitionIdMin + 1;
    // popular면 스크랩이 몰린 낮은 id 쪽을 더 자주 고른다.
    const offset = popular ? Math.floor(Math.pow(Math.random(), 2.2) * span) : Math.floor(Math.random() * span);
    if (offset % 20 > 1) return competitionIdMin + offset;
  }
}

function pickPage() {
  const roll = Math.random();
  if (roll < 0.6) return 0;
  if (roll < 0.85) return 1 + Math.floor(Math.random() * 3);
  return 4 + Math.floor(Math.random() * 46);
}

function currentStage() {
  const elapsed = exec.instance.currentTestRunDuration / 1000;
  for (const period of stagePeriods) {
    if (elapsed >= period.holdFrom && elapsed < period.to) return period.label;
  }
  return 'ramp';
}

export default function (data) {
  const session = data.tokens[(exec.vu.idInTest - 1) % data.tokens.length];
  const name = pickEndpoint();
  const stage = currentStage();
  const params = {
    headers: { Authorization: `Bearer ${session.token}`, Accept: 'application/json' },
    tags: { name, stage },
  };
  const size = Math.random() < 0.7 ? 10 : 20;
  let response;
  switch (name) {
    case 'list_latest':
      response = http.get(`${baseUrl}/api/competitions?sort=LATEST&page=${pickPage()}&size=${size}`, params); break;
    case 'list_deadline':
      response = http.get(`${baseUrl}/api/competitions?sort=DEADLINE&page=${pickPage()}&size=${size}`, params); break;
    case 'list_views':
      response = http.get(`${baseUrl}/api/competitions?sort=VIEWS&page=${pickPage()}&size=${size}`, params); break;
    case 'list_scraps':
      response = http.get(`${baseUrl}/api/competitions?sort=SCRAPS&page=${pickPage()}&size=${size}`, params); break;
    case 'list_keyword': {
      const keyword = encodeURIComponent(keywords[Math.floor(Math.random() * keywords.length)]);
      response = http.get(`${baseUrl}/api/competitions?keyword=${keyword}&page=${pickPage()}&size=${size}`, params); break;
    }
    case 'list_category': {
      const category = categories[Math.floor(Math.random() * categories.length)];
      response = http.get(`${baseUrl}/api/competitions?category=${category}&sort=DEADLINE&page=${pickPage()}&size=${size}`, params); break;
    }
    case 'top5':
      response = http.get(`${baseUrl}/api/competitions/top5`, params); break;
    case 'closing_soon':
      response = http.get(`${baseUrl}/api/competitions/closing-soon?page=${pickPage() % 5}&size=${size}`, params); break;
    case 'detail':
      response = http.get(`${baseUrl}/api/competitions/${pickOpenCompetitionId(true)}`, params); break;
    case 'scrap_add':
      response = http.post(`${baseUrl}/api/competitions/${pickOpenCompetitionId(true)}/scrap`, null, params); break;
    case 'scrap_remove':
      response = http.del(`${baseUrl}/api/competitions/${pickOpenCompetitionId(true)}/scrap`, null, params); break;
  }
  check(response, { 'HTTP 200': (r) => r.status === 200 }, { name, stage });
  if (thinkMaxSeconds > 0) sleep(Math.random() * thinkMaxSeconds);
}

function metric(data, key, field, fallback = 0) {
  return data.metrics[key]?.values?.[field] ?? fallback;
}

function ms(value) {
  return Number(value).toFixed(1);
}

export function handleSummary(data) {
  const stageRows = stagePeriods.map((period) => {
    const count = metric(data, `http_reqs{stage:${period.label}}`, 'count');
    const duration = (key) => metric(data, `http_req_duration{stage:${period.label}}`, key);
    const failed = metric(data, `http_req_failed{stage:${period.label}}`, 'rate') * 100;
    return `| ${period.level} | ${count} | ${(count / stageHoldSeconds).toFixed(1)} | ${ms(duration('avg'))} | ${ms(duration('med'))} | ${ms(duration('p(95)'))} | ${ms(duration('p(99)'))} | ${ms(duration('max'))} | ${failed.toFixed(2)}% |`;
  }).join('\n');

  const endpointRows = endpoints.map(([name, weight]) => {
    const duration = (key) => metric(data, `http_req_duration{name:${name}}`, key);
    const failed = metric(data, `http_req_failed{name:${name}}`, 'rate') * 100;
    return `| ${name} | ${weight}% | ${ms(duration('avg'))} | ${ms(duration('med'))} | ${ms(duration('p(95)'))} | ${ms(duration('p(99)'))} | ${ms(duration('max'))} | ${failed.toFixed(2)}% |`;
  }).join('\n');

  const markdown = `# k6 공모전 API 단계별 부하 테스트

## 조건

- 기준 URL: \`${baseUrl}\`
- 단계: 동시 사용자 ${levels.join(' → ')}명, 단계마다 증가 ${rampSeconds}초 + 유지 ${stageHoldSeconds}초
- 사용자마다 요청 사이 0~${thinkMaxSeconds}초 무작위 대기
- 테스트 사용자 토큰 ${tokenCount}개를 VU에 나눠 씀
- 전체 요청 ${metric(data, 'http_reqs', 'count')}건, 실패율 ${(metric(data, 'http_req_failed', 'rate') * 100).toFixed(2)}%

## 단계별 결과 (유지 구간만)

| 동시 사용자 | 요청 수 | 처리량(req/s) | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | 최대(ms) | 실패율 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
${stageRows}

## API별 결과 (전체 구간)

| API | 비중 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | 최대(ms) | 실패율 |
|---|---:|---:|---:|---:|---:|---:|---:|
${endpointRows}
`;
  return { [resultPath]: markdown, stdout: `${markdown}\n` };
}
