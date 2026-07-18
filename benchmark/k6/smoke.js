
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';
const COUPON_ID = __ENV.COUPON_ID || '1';

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],     // 실패(5xx/네트워크) 1% 미만
    http_req_duration: ['p(95)<300'],   // 조회는 가벼우니 여유 있게
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
};

export default function () {
  const res = http.get(`${BASE}/api/v1/coupons/${COUPON_ID}`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body has remaining': (r) => r.body && r.body.includes('remaining'),
  });
}
