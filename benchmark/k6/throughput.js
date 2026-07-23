
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from './jwt.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';
const STRATEGY = __ENV.STRATEGY || 'redis';
const COUPON_ID = __ENV.COUPON_ID || '1';

// 201/409/5xx를 분리 집계 — 409(품절)는 정상 비즈니스 응답이므로 실패로 보지 않는다.
const created = new Counter('coupon_created');
const soldOut = new Counter('coupon_sold_out');
const serverError = new Counter('coupon_server_error');
const issueDuration = new Trend('coupon_issue_duration', true);

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '30s', target: 200 },
        { duration: '30s', target: 400 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    coupon_server_error: ['count<1'],     // 5xx는 0이어야 함
    http_req_duration: ['p(95)<200'],     // 목표치는 1차 측정 후 역산해 조정
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  // userId는 numeric(Long)이어야 함. VU/ITER로 유일 숫자 생성.
  const userId = __VU * 10000000 + __ITER;
  const url = `${BASE}/api/v1/coupons/${COUPON_ID}/issue?strategy=${STRATEGY}`;
  // 발급 주체는 검증된 토큰의 sub로만 전달된다. 이 벤치마크는 게이트웨이를 우회해
  // coupon-api를 직접 때리지만, coupon-api가 토큰을 재검증하므로 서명된 토큰이 필요하다.
  const res = http.post(url, null, {
    headers: authHeaders(userId),
  });

  issueDuration.add(res.timings.duration);
  if (res.status === 201) created.add(1);
  else if (res.status === 409) soldOut.add(1);
  else serverError.add(1);
}
