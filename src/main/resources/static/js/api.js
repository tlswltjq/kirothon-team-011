// 공통 API 도우미. 화면 작업 9·10이 함께 쓴다. 계약: design.md "요청·응답 JSON 계약".
// 토큰은 localStorage에 두고 모든 /api 요청에 X-Anon-Token 헤더로 보낸다.
const TOKEN_KEY = 'anonToken';
const NICK_KEY = 'anonNickname';

export class ApiError extends Error {
  constructor(status, code, message, fields) {
    super(message || code || `HTTP ${status}`);
    this.status = status;
    this.code = code;
    this.fields = fields || {};
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getNickname() {
  return localStorage.getItem(NICK_KEY);
}

export function saveIdentity(token, nickname) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(NICK_KEY, nickname);
}

export function clearIdentity() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(NICK_KEY);
}

async function toError(res) {
  let body = {};
  try {
    body = await res.json();
  } catch (e) {
    // 본문 없음
  }
  return new ApiError(res.status, body.code, body.message, body.fields);
}

// JSON 요청. body가 FormData면 그대로, 객체면 JSON으로 보낸다. 204/201 본문 없음은 null.
export async function api(path, { method = 'GET', body } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers['X-Anon-Token'] = token;
  let payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(path, { method, headers, body: payload });
  if (res.status === 401) {
    clearIdentity();
  }
  if (!res.ok) throw await toError(res);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// 토큰이 필요한 PNG를 blob URL로 받는다. 다 쓴 URL은 URL.revokeObjectURL로 해제한다.
export async function imageUrl(path) {
  const res = await fetch(path, { headers: { 'X-Anon-Token': getToken() || '' } });
  if (!res.ok) throw await toError(res);
  return URL.createObjectURL(await res.blob());
}

// 닉네임으로 익명 사용자를 만든다(이미 토큰이 있으면 그대로 쓴다).
export async function ensureUser(nickname) {
  if (getToken()) return { token: getToken(), nickname: getNickname() };
  const res = await api('/api/users', { method: 'POST', body: { nickname } });
  saveIdentity(res.token, res.nickname);
  return res;
}

// 텍스트만 넣는 요소 생성(innerHTML 금지 규칙).
export function el(tag, { text, cls, attrs } = {}, ...children) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = String(text);
  if (cls) node.className = cls;
  if (attrs) for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  for (const c of children) if (c) node.appendChild(c);
  return node;
}

// 오류를 사용자 문구로.
export function errorText(err) {
  if (err instanceof ApiError) return err.message || err.code;
  return '네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
}
