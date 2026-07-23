import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

// user-service와 같은 서명 키. coupon-api가 이 키로 토큰을 재검증한다.
const SECRET = __ENV.JWT_SECRET;

// 클레임 이름은 user-service JwtTokenProvider가 발급하는 것과 정확히 같아야 한다.
// sub는 반드시 숫자 문자열 — coupon-api가 Long으로 파싱한다.
const TOKEN_VALIDITY_SECONDS = 3600;

function b64url(value) {
  return encoding.b64encode(value, 'rawurl');
}

/**
 * VU마다 유일한 userId로 Access Token을 서명한다.
 *
 * 사전에 사용자를 만들어 로그인하는 방식도 있지만, BCrypt 때문에 setup만 수십 초가 걸리고
 * 발급 수만큼 계정이 필요하다. 여기서는 서명 키를 알고 있으므로 직접 만드는 편이 빠르고
 * userId 유일성(중복 발급 방지 로직이 이걸 본다)도 그대로 유지된다.
 */
export function accessToken(userId, role = 'USER') {
  if (!SECRET) {
    throw new Error('JWT_SECRET 환경변수가 필요합니다. 예: k6 run -e JWT_SECRET=$JWT_SECRET script.js');
  }

  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(
    JSON.stringify({
      sub: String(userId),
      username: `bench-${userId}`,
      role: role,
      type: 'access',
      iat: now,
      exp: now + TOKEN_VALIDITY_SECONDS,
    })
  );

  const data = `${header}.${payload}`;
  const signature = crypto.hmac('sha256', SECRET, data, 'base64rawurl');
  return `${data}.${signature}`;
}

export function authHeaders(userId, role) {
  return { Authorization: `Bearer ${accessToken(userId, role)}` };
}
