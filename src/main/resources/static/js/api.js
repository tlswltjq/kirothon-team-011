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

// 익명 사용자 발급(ux-flow 4절). 토큰이 있으면 그대로 쓰고, 동시 호출은 같은 Promise를 공유한다.
let anonPromise = null;
export function ensureAnon() {
  if (getToken()) return Promise.resolve({ token: getToken(), nickname: getNickname() });
  if (!anonPromise) {
    const n = new Uint32Array(1);
    crypto.getRandomValues(n);
    const nickname = '익명' + String(1000 + (n[0] % 9000));
    anonPromise = api('/api/users', { method: 'POST', body: { nickname } })
      .then((res) => {
        saveIdentity(res.token, res.nickname);
        return res;
      })
      .finally(() => {
        anonPromise = null;
      });
  }
  return anonPromise;
}

// 페이지 이미지를 wrap 안에 넣는다. 성공하면 blob URL(해제는 호출한 화면이 맡는다), 실패하면 null.
// 실패해도 throw 하지 않아 Promise.all로 여러 쪽을 받아도 다른 쪽은 그대로 그려진다.
export async function loadPageImage(wrap, path, alt) {
  for (const old of wrap.querySelectorAll('[data-page-image]')) old.remove();
  wrap.classList.add('loading');
  const holder = el('div', { cls: 'page-fallback', attrs: { 'data-page-image': '1' } });
  wrap.insertBefore(holder, wrap.firstChild);
  try {
    const url = await imageUrl(path);
    const img = el('img', { attrs: { alt: alt || '', src: url, 'data-page-image': '1' } });
    holder.remove();
    wrap.insertBefore(img, wrap.firstChild);
    wrap.classList.remove('loading');
    return url;
  } catch (e) {
    wrap.classList.remove('loading');
    const btn = el('button', { text: '다시 불러오기', cls: 'secondary', attrs: { type: 'button' } });
    btn.addEventListener('click', () => loadPageImage(wrap, path, alt));
    holder.replaceChildren(el('p', { text: '이미지를 불러오지 못했어요.', cls: 'muted' }), btn);
    return null;
  }
}

// container 맨 위에 배너를 ms(기본 4000) 동안 보인다. 새 배너가 뜨면 이전 것을 지우고 타이머를 다시 잰다.
const bannerTimers = new WeakMap();
export function showBanner(container, text, kind, ms = 4000) {
  if (!container) return null;
  clearTimeout(bannerTimers.get(container));
  for (const old of container.querySelectorAll(':scope > .banner')) old.remove();
  const banner = el('div', { text, cls: 'banner' + (kind ? ' ' + kind : ''), attrs: { role: 'status' } });
  container.insertBefore(banner, container.firstChild);
  bannerTimers.set(container, setTimeout(() => banner.remove(), ms));
  return banner;
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
