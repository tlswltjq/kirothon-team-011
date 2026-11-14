import { ensureUser, getToken, getNickname, el, errorText, ApiError } from './api.js';
import { renderNav } from './nav.js';

const $ = (id) => document.getElementById(id);

function showMenu() {
  $('start').classList.add('hidden');
  $('menu').classList.remove('hidden');
  $('welcome').textContent = `${getNickname() || ''}님, 무엇을 할까요?`;
  $('notice').classList.remove('hidden');
  renderNav();
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
