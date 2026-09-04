import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from './jwt.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';
const COUPON_ID = __ENV.COUPON_ID || '1';

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
    coupon_server_error: ['count<1'],
    http_req_duration: ['p(95)<200'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const userId = __VU * 10000000 + __ITER;
  const url = `${BASE}/api/v1/coupons/${COUPON_ID}/issue`;
  const res = http.post(url, null, {
    headers: authHeaders(userId),
  });

  issueDuration.add(res.timings.duration);
  if (res.status === 202) created.add(1);
  else if (res.status === 409) soldOut.add(1);
  else serverError.add(1);
}
