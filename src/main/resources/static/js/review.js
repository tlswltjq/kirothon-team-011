import { api, imageUrl, el, errorText, getToken, ApiError } from './api.js';
import { renderNav } from './nav.js';

const $ = (id) => document.getElementById(id);
const MAX_PINS = 50;

const ERRORS = {
  UNAUTHENTICATED: '로그인 정보가 없습니다. 홈에서 닉네임으로 다시 시작해 주세요.',
  DUPLICATE_REVIEW: '이미 평가한 이력서입니다. 다음 이력서를 받아 주세요.',
  SELF_REVIEW: '내 이력서는 평가할 수 없습니다. 다른 이력서를 받아 주세요.',
  NOT_FOUND: '평가할 수 없는 이력서입니다(비공개로 바뀌었거나 없습니다). 다른 이력서를 받아 주세요.',
};

const state = { target: null, pins: [], selected: -1, urls: [], busy: false, done: false };

function message(err) {
  if (err instanceof ApiError) {
    if (err.code === 'VALIDATION_FAILED') {
      const f = Object.entries(err.fields || {}).map(([k, v]) => `${k}: ${v}`).join(' / ');
      return `입력값을 확인해 주세요.${f ? ' ' + f : ''}`;
    }
    if (ERRORS[err.code]) return ERRORS[err.code];
  }
  return errorText(err);
}

function status(text, kind) {
  $('status').replaceChildren(text ? el('div', { text, cls: `msg ${kind || ''}` }) : null);
}

function resetView() {
  for (const u of state.urls) URL.revokeObjectURL(u);
  state.urls = [];
  state.target = null;
  state.pins = [];
  state.selected = -1;
  state.done = false;
  $('review-area').classList.add('hidden');
  $('done-area').classList.add('hidden');
  $('pages').replaceChildren();
  $('summary').value = '';
  $('summary-len').textContent = '0';
  $('form-error').replaceChildren();
  for (const r of document.querySelectorAll('input[name=score]')) r.checked = false;
}

async function loadNext(skip) {
  if (state.busy) return;
  state.busy = true;
  resetView();
  status('평가할 이력서를 찾는 중입니다...');
  try {
    const q = skip ? `?skip=${encodeURIComponent(skip)}` : '';
    const target = await api(`/api/review-queue/next${q}`);
    if (!target) {
      status('지금 평가할 이력서가 없습니다. 잠시 후 다시 확인해 주세요.', 'warn');
      return;
    }
    state.target = target;
    status('');
    $('target-role').textContent = `지원 직무: ${target.targetRole}`;
    $('target-version').textContent = `(v${target.versionNo}, ${target.pageCount}쪽)`;
    $('review-area').classList.remove('hidden');
    buildPages();
    renderPins();
  } catch (err) {
    if (err instanceof ApiError && err.status === 404 && err.code === 'NO_REVIEWABLE_RESUME') {
      status('지금 평가할 이력서가 없습니다. 잠시 후 다시 확인해 주세요.', 'warn');
    } else {
      status(message(err), 'error');
    }
    if (err instanceof ApiError && err.status === 401) setTimeout(() => location.replace('/index.html'), 1500);
  } finally {
    state.busy = false;
  }
}

function buildPages() {
  const host = $('pages');
  const t = state.target;
  for (let p = 1; p <= t.pageCount; p++) {
    const img = el('img', { attrs: { alt: `${p}쪽 마스킹된 이력서`, draggable: 'false' } });
    const wrap = el('div', { cls: 'page-wrap clickable', attrs: { 'data-page': String(p) } }, img);
    img.addEventListener('click', (e) => {
      if (state.done) return;
      const rect = img.getBoundingClientRect();
      if (!rect.width || !rect.height) return;
      addPin(p, (e.clientX - rect.left) / rect.width, (e.clientY - rect.top) / rect.height);
    });
    host.appendChild(el('div', {}, el('div', { text: `${p} / ${t.pageCount}쪽`, cls: 'page-label' }), wrap));
    const versionId = t.versionId;
    imageUrl(`/api/versions/${versionId}/pages/${p}`).then((url) => {
      if (!state.target || state.target.versionId !== versionId) { URL.revokeObjectURL(url); return; }
      state.urls.push(url);
      img.src = url;
    }).catch((err) => {
      wrap.appendChild(el('div', { text: `이미지를 불러오지 못했습니다. ${message(err)}`, cls: 'msg error' }));
    });
  }
}

function clamp01(n) {
  return Math.min(1, Math.max(0, n));
}

function addPin(page, x, y) {
  if (state.pins.length >= MAX_PINS) {
    $('form-error').replaceChildren(el('div', { text: `핀은 최대 ${MAX_PINS}개까지 만들 수 있습니다.`, cls: 'msg error' }));
    return;
  }
  $('form-error').replaceChildren();
  state.pins.push({ page, x: clamp01(x), y: clamp01(y), comment: '' });
  state.selected = state.pins.length - 1;
  renderPins();
  const ta = document.querySelector(`#pin-list .pin-item[data-idx="${state.selected}"] textarea`);
  if (ta) ta.focus();
}

function select(idx) {
  state.selected = idx;
  for (const node of document.querySelectorAll('.page-wrap .pin')) {
    node.classList.toggle('active', Number(node.dataset.idx) === idx);
  }
  for (const node of document.querySelectorAll('#pin-list .pin-item')) {
    node.classList.toggle('active', Number(node.dataset.idx) === idx);
  }
}

function renderPins() {
  for (const node of document.querySelectorAll('.page-wrap .pin')) node.remove();
  state.pins.forEach((pin, idx) => {
    const wrap = document.querySelector(`.page-wrap[data-page="${pin.page}"]`);
    if (!wrap) return;
    const marker = el('button', { text: String(idx + 1), cls: 'pin' + (idx === state.selected ? ' active' : ''),
      attrs: { type: 'button', 'data-idx': String(idx), 'aria-label': `핀 ${idx + 1}` } });
    marker.style.left = `${pin.x * 100}%`;
    marker.style.top = `${pin.y * 100}%`;
    marker.addEventListener('click', (e) => {
      e.stopPropagation();
      select(idx);
      const ta = document.querySelector(`#pin-list .pin-item[data-idx="${idx}"] textarea`);
      if (ta) { ta.scrollIntoView({ block: 'center' }); ta.focus(); }
    });
    wrap.appendChild(marker);
  });

  const list = $('pin-list');
  list.replaceChildren();
  state.pins.forEach((pin, idx) => {
    const del = el('button', { text: '삭제', cls: 'danger', attrs: { type: 'button' } });
    del.addEventListener('click', () => {
      state.pins.splice(idx, 1);
      state.selected = -1;
      renderPins();
    });
    const ta = el('textarea', { attrs: { maxlength: '500', 'aria-label': `핀 ${idx + 1} 코멘트`, placeholder: '이 위치에 대한 코멘트 (1~500자)' } });
    ta.value = pin.comment;
    ta.addEventListener('input', () => {
      pin.comment = ta.value;
      count.textContent = `${ta.value.length} / 500`;
    });
    ta.addEventListener('focus', () => select(idx));
    const count = el('span', { text: `${pin.comment.length} / 500`, cls: 'muted' });
    list.appendChild(el('div', { cls: 'pin-item' + (idx === state.selected ? ' active' : ''), attrs: { 'data-idx': String(idx) } },
      el('div', { cls: 'head' },
        el('span', {}, el('span', { text: String(idx + 1), cls: 'pin' }), el('span', { text: `${pin.page}쪽` })),
        del),
      ta, count));
  });
  $('pin-count').textContent = `(${state.pins.length} / ${MAX_PINS})`;
  $('pin-empty').classList.toggle('hidden', state.pins.length > 0);
}

$('summary').addEventListener('input', () => {
  $('summary-len').textContent = String($('summary').value.length);
});

$('review-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (state.busy || !state.target) return;
  const err = $('form-error');
  err.replaceChildren();
  const problems = [];
  const summary = $('summary').value.trim();
  if (summary.length < 20 || summary.length > 1000) problems.push('총평은 공백을 뺀 20~1000자여야 합니다.');
  const checked = document.querySelector('input[name=score]:checked');
  if (!checked) problems.push('총점(1~5)을 선택해 주세요.');
  state.pins.forEach((pin, idx) => {
    const c = pin.comment.trim();
    if (c.length < 1 || c.length > 500) problems.push(`핀 ${idx + 1}의 코멘트는 공백을 뺀 1~500자여야 합니다.`);
  });
  if (problems.length) {
    err.appendChild(el('div', { cls: 'msg error' }, ...problems.map((t) => el('div', { text: t }))));
    return;
  }
  state.busy = true;
  $('submit-btn').disabled = true;
  try {
    await api(`/api/versions/${state.target.versionId}/reviews`, {
      method: 'POST',
      body: {
        totalScore: Number(checked.value),
        summary,
        pins: state.pins.map((p) => ({ page: p.page, x: p.x, y: p.y, comment: p.comment.trim() })),
      },
    });
    state.done = true;
    $('review-area').classList.add('hidden');
    $('done-area').classList.remove('hidden');
    status('');
  } catch (ex) {
    err.appendChild(el('div', { text: message(ex), cls: 'msg error' }));
  } finally {
    state.busy = false;
    $('submit-btn').disabled = false;
  }
});

$('other-btn').addEventListener('click', () => {
  if (state.pins.length || $('summary').value.trim()) {
    if (!window.confirm('작성 중인 핀과 총평이 사라집니다. 다른 이력서를 받을까요?')) return;
  }
  loadNext(state.target ? state.target.versionId : null);
});

$('next-btn').addEventListener('click', () => loadNext(null));

renderNav();
if (!getToken()) {
  location.replace('/index.html');
} else {
  loadNext(null);
}
