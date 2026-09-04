import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

const SECRET = __ENV.JWT_SECRET;

const TOKEN_VALIDITY_SECONDS = 3600;

function b64url(value) {
  return encoding.b64encode(value, 'rawurl');
}

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
