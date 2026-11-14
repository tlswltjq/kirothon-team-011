import { api, ensureAnon, getToken, el, errorText, ApiError } from './api.js';
import { renderNav } from './nav.js';

const $ = (id) => document.getElementById(id);

renderNav();
if (getToken()) $('revisit').classList.remove('hidden');

// 데모 프로필일 때만 샘플 데이터 버튼을 보인다(요구사항 12.2).
async function showDemo() {
  try {
    const res = await fetch('/api/demo');
    if (res.ok && (await res.json()).enabled) $('demo').classList.remove('hidden');
  } catch (err) {
    // 상태를 못 읽으면 버튼을 숨긴 채 둔다.
  }
}
showDemo();

$('demo-btn').addEventListener('click', async () => {
  const msg = $('demo-msg');
  const btn = $('demo-btn');
  msg.replaceChildren();
  btn.disabled = true;
  btn.textContent = '넣는 중…';
  try {
    await ensureAnon();
    await api('/api/demo/seed', { method: 'POST' });
    location.href = '/my.html';
  } catch (err) {
    btn.disabled = false;
    btn.textContent = '데모용 샘플 데이터 넣기';
    if (err instanceof ApiError && (err.status === 409 || err.code === 'DEMO_ALREADY_SEEDED')) {
      msg.appendChild(el('div', { text: '이미 샘플 데이터를 넣었어요. ', cls: 'msg warn' },
        el('a', { text: '내 이력서 보기', attrs: { href: '/my.html' } })));
    } else {
      msg.appendChild(el('div', { text: '연결에 실패했어요. 잠시 후 다시 눌러 주세요.', cls: 'msg error' }));
    }
  }
});
