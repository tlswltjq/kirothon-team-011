import { api, ensureUser, getToken, getNickname, el, errorText, ApiError } from './api.js';
import { renderNav } from './nav.js';

const $ = (id) => document.getElementById(id);

function showMenu() {
  $('start').classList.add('hidden');
  $('menu').classList.remove('hidden');
  $('welcome').textContent = `${getNickname() || ''}님, 무엇을 할까요?`;
  $('notice').classList.remove('hidden');
  renderNav();
  showDemo();
}

// 데모 프로필일 때만 샘플 데이터 버튼을 보인다(요구사항 12.2).
async function showDemo() {
  try {
    const res = await fetch('/api/demo');
    if (res.ok && (await res.json()).enabled) $('demo').classList.remove('hidden');
  } catch (err) {
    // 상태를 못 읽으면 버튼을 숨긴 채 둔다.
  }
}

renderNav();
if (getToken()) {
  showMenu();
} else {
  $('start').classList.remove('hidden');
}

$('start-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('nickname-error').textContent = '';
  $('start-msg').replaceChildren();
  const nickname = $('nickname').value.trim();
  if (nickname.length < 2 || nickname.length > 20) {
    $('nickname-error').textContent = '닉네임은 공백을 뺀 2~20자여야 합니다.';
    return;
  }
  $('start-btn').disabled = true;
  try {
    await ensureUser(nickname);
    showMenu();
  } catch (err) {
    const text = err instanceof ApiError && err.code === 'VALIDATION_FAILED'
      ? '닉네임은 공백을 뺀 2~20자여야 합니다.' : errorText(err);
    $('start-msg').appendChild(el('div', { text, cls: 'msg error' }));
  } finally {
    $('start-btn').disabled = false;
  }
});

$('demo-btn').addEventListener('click', async () => {
  $('demo-msg').replaceChildren();
  $('demo-btn').disabled = true;
  try {
    await api('/api/demo/seed', { method: 'POST' });
    location.href = '/my.html';
  } catch (err) {
    const text = err instanceof ApiError && err.code === 'DEMO_ALREADY_SEEDED'
      ? '이미 샘플 데이터를 넣었습니다. 내 이력서에서 확인하세요.' : errorText(err);
    $('demo-msg').appendChild(el('div', { text, cls: 'msg error' }));
    $('demo-btn').disabled = false;
  }
});
