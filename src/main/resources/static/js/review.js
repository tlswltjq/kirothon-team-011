import { api, el, errorText, ApiError, ensureAnon, loadPageImage, showBanner } from './api.js';
import { renderNav } from './nav.js';

const $ = (id) => document.getElementById(id);
const MAX_PINS = 50;
const MIN_SUMMARY = 20;

const state = { target: null, pins: [], selected: -1, urls: [], busy: false, loadSeq: 0 };

function banner(text, kind) {
  showBanner($('done-area'), text, kind || 'ok', 4000);
}

function status(text, kind, retry) {
  const nodes = [];
  if (text) nodes.push(el('div', { text, cls: `msg ${kind || ''}` }));
  if (retry) {
    const b = el('button', { text: '다시 확인', cls: 'secondary', attrs: { type: 'button' } });
    b.addEventListener('click', () => loadNext(null));
    nodes.push(b);
  }
  $('status').replaceChildren(...nodes);
}

function reviewedCount() {
  return Number(sessionStorage.getItem('reviewedCount') || '0');
}

// 작성 상태와 화면만 비운다. 배너(#done-area)는 건드리지 않는다.
function resetView() {
  for (const u of state.urls) URL.revokeObjectURL(u);
  state.urls = [];
  state.target = null;
  state.pins = [];
  state.selected = -1;
  $('review-area').classList.add('hidden');
  $('empty-area').classList.add('hidden');
  $('pages').replaceChildren();
  $('pin-list').replaceChildren();
  $('summary').value = '';
  updateCounter();
  clearErrors();
  for (const r of document.querySelectorAll('input[name=score]')) r.checked = false;
}

function clearErrors() {
  $('form-error').replaceChildren();
  $('pin-error').replaceChildren();
  $('summary-error').textContent = '';
  $('score-error').textContent = '';
}

function updateCounter() {
  const n = $('summary').value.trim().length;
  const c = $('summary-len');
  if (n >= MIN_SUMMARY) {
    c.textContent = `${n}자 · 좋아요`;
    c.className = 'counter ok';
  } else {
    c.textContent = `${n} / ${MIN_SUMMARY}자 이상`;
    c.className = 'counter short';
  }
}

function fetchNext(skip) {
  const q = skip ? `?skip=${encodeURIComponent(skip)}` : '';
  return api(`/api/review-queue/next${q}`);
}

// opts.afterSubmit: 제출 직후. opts.prevId: 건너뛰기 전 versionId.
async function loadNext(skip, opts = {}) {
  if (state.busy) return;
  state.busy = true;
  const seq = ++state.loadSeq;
  resetView();
  status('평가할 이력서를 고르는 중…');
  try {
    await ensureAnon();
    let target;
    try {
      target = await fetchNext(skip);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        await ensureAnon(); // api.js가 지운 토큰을 새로 받고 한 번만 다시 요청
        target = await fetchNext(skip);
      } else {
        throw err;
      }
    }
    if (seq !== state.loadSeq) return;
    if (!target) {
      showEmpty(opts.afterSubmit);
      return;
    }
    if (opts.prevId && target.versionId === opts.prevId) {
      banner('다른 이력서가 없어 같은 이력서를 다시 보여 드려요.', 'warn');
    }
    state.target = target;
    status('');
    $('target-role').textContent = `지원 직무: ${target.targetRole}`;
    $('target-version').textContent = `· v${target.versionNo} · ${target.pageCount}쪽`;
    $('review-area').classList.remove('hidden');
    buildPages();
    renderPins();
  } catch (err) {
    if (err instanceof ApiError) {
      status(errorText(err), 'error', true);
    } else {
      status('연결에 실패했어요.', 'error', true);
    }
  } finally {
    state.busy = false;
  }
}

function showEmpty(afterSubmit) {
  status('');
  const n = reviewedCount();
  const count = $('empty-count');
  if (n > 0 && !afterSubmit) {
    count.textContent = `이번에 ${n}개를 평가했어요. 고마워요!`;
    count.classList.remove('hidden');
  } else {
    count.classList.add('hidden');
  }
  $('empty-area').classList.remove('hidden');
}

function buildPages() {
  const host = $('pages');
  const t = state.target;
  const versionId = t.versionId;
  for (let p = 1; p <= t.pageCount; p++) {
    const wrap = el('div', { cls: 'page-wrap clickable', attrs: { 'data-page': String(p) } });
    wrap.addEventListener('click', (e) => {
      if (e.target.closest('button')) return;
      const img = wrap.querySelector('img');
      if (!img) return;
      const rect = img.getBoundingClientRect();
      if (!rect.width || !rect.height) return;
      addPin(p, (e.clientX - rect.left) / rect.width, (e.clientY - rect.top) / rect.height);
    });
    host.appendChild(el('div', {}, el('div', { text: `${p} / ${t.pageCount}쪽`, cls: 'page-label' }), wrap));
    const track = (url) => {
      if (!state.target || state.target.versionId !== versionId) { URL.revokeObjectURL(url); return; }
      state.urls.push(url);
    };
    Promise.resolve(loadPageImage(wrap, `/api/versions/${versionId}/pages/${p}`, `${p}쪽 마스킹된 이력서`, track))
      .catch(() => { /* 실패 표시와 다시 불러오기는 loadPageImage가 맡는다 */ });
  }
}

function clamp01(n) {
  return Math.min(1, Math.max(0, n));
}

function addPin(page, x, y) {
  if (state.pins.length >= MAX_PINS) {
    $('pin-error').replaceChildren(el('div', { text: `핀은 최대 ${MAX_PINS}개까지 만들 수 있어요.`, cls: 'msg error' }));
    return;
  }
  $('pin-error').replaceChildren();
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
    const count = el('span', { text: `${pin.comment.length} / 500`, cls: 'muted' });
    const ta = el('textarea', { attrs: { maxlength: '500', 'aria-label': `핀 ${idx + 1} 코멘트`,
      placeholder: '이 위치에 대한 코멘트 (예: 성과에 숫자를 넣어 보세요)' } });
    ta.value = pin.comment;
    ta.addEventListener('input', () => {
      pin.comment = ta.value;
      count.textContent = `${ta.value.length} / 500`;
    });
    ta.addEventListener('focus', () => select(idx));
    list.appendChild(el('div', { cls: 'pin-item' + (idx === state.selected ? ' active' : ''), attrs: { 'data-idx': String(idx) } },
      el('div', { cls: 'head' },
        el('span', {}, el('span', { text: String(idx + 1), cls: 'pin' }), el('span', { text: `${pin.page}쪽` })),
        del),
      ta, count, el('div', { cls: 'field-error pin-err' })));
  });
  $('pin-count').textContent = `(${state.pins.length} / ${MAX_PINS})`;
  $('pin-empty').classList.toggle('hidden', state.pins.length > 0);
}

$('summary').addEventListener('input', updateCounter);

function firstScroll(node) {
  if (node) node.scrollIntoView({ block: 'center' });
}

$('review-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (state.busy || !state.target) return;
  clearErrors();
  for (const n of document.querySelectorAll('.pin-err')) n.textContent = '';
  let first = null;
  const summary = $('summary').value.trim();
  if (summary.length < MIN_SUMMARY || summary.length > 1000) {
    $('summary-error').textContent = '총평을 20자 이상 써 주세요. 공백은 세지 않아요.';
    first = first || $('summary');
  }
  const checked = document.querySelector('input[name=score]:checked');
  if (!checked) {
    $('score-error').textContent = '총점을 1~5 중에서 골라 주세요.';
    first = first || $('score');
  }
  state.pins.forEach((pin, idx) => {
    const c = pin.comment.trim();
    if (c.length < 1 || c.length > 500) {
      const item = document.querySelector(`#pin-list .pin-item[data-idx="${idx}"]`);
      const slot = item && item.querySelector('.pin-err');
      if (slot) slot.textContent = `핀 ${idx + 1}의 코멘트를 1~500자로 써 주세요.`;
      first = first || item;
    }
  });
  if (first) { firstScroll(first); return; }

  const versionId = state.target.versionId;
  state.busy = true;
  const btn = $('submit-btn');
  btn.disabled = true;
  btn.textContent = '보내는 중…';
  let outcome = null;
  try {
    await api(`/api/versions/${versionId}/reviews`, {
      method: 'POST',
      body: {
        totalScore: Number(checked.value),
        summary,
        pins: state.pins.map((p) => ({ page: p.page, x: p.x, y: p.y, comment: p.comment.trim() })),
      },
    });
    sessionStorage.setItem('reviewedCount', String(reviewedCount() + 1));
    outcome = { text: '익명으로 전달했어요. 다음 이력서예요.', kind: 'ok', afterSubmit: true };
  } catch (ex) {
    if (ex instanceof ApiError && (ex.status === 409 || ex.status === 404)) {
      outcome = { text: '이미 평가했거나 내려간 이력서라 다음 이력서로 넘어왔어요.', kind: 'warn' };
    } else if (ex instanceof ApiError && ex.status === 403) {
      outcome = { text: '내 이력서라 건너뛰었어요.', kind: 'warn' };
    } else if (ex instanceof ApiError && ex.status === 400) {
      const f = ex.fields || {};
      let focus = null;
      const rest = [];
      for (const [key, text] of Object.entries(f)) {
        const pm = /^pins\[(\d+)\]/.exec(key);
        if (key === 'summary') {
          $('summary-error').textContent = text;
          focus = focus || $('summary');
        } else if (key === 'totalScore') {
          $('score-error').textContent = text;
          focus = focus || $('score');
        } else if (pm) {
          const item = document.querySelector(`#pin-list .pin-item[data-idx="${pm[1]}"]`);
          const slot = item && item.querySelector('.pin-err');
          if (slot) {
            slot.textContent = slot.textContent ? `${slot.textContent} ${text}` : text;
            focus = focus || item.querySelector('textarea') || item;
          } else {
            rest.push(text);
          }
        } else {
          rest.push(text);
        }
      }
      $('form-error').appendChild(el('div', { text: rest.length ? `입력을 확인해 주세요. ${rest.join(' / ')}` : '입력을 확인해 주세요.', cls: 'msg error' }));
      if (focus) {
        if (typeof focus.focus === 'function') focus.focus({ preventScroll: true });
        firstScroll(focus);
      }
    } else if (ex instanceof ApiError) {
      $('form-error').appendChild(el('div', { text: errorText(ex), cls: 'msg error' }));
    } else {
      $('form-error').appendChild(el('div', { text: '연결에 실패했어요. 다시 보내 주세요.', cls: 'msg error' }));
    }
  } finally {
    state.busy = false;
    btn.disabled = false;
    btn.textContent = '평가 보내고 다음 이력서';
  }
  if (outcome) {
    banner(outcome.text, outcome.kind);
    // 같은 이력서가 다시 오지 않도록 방금 낸 versionId를 건너뛴다(후보가 하나뿐이면 서버가 그대로 돌려준다)
    await loadNext(outcome.afterSubmit || outcome.kind === 'warn' ? versionId : null, { afterSubmit: outcome.afterSubmit });
  }
});

$('other-btn').addEventListener('click', () => {
  if (state.pins.length || $('summary').value.trim()) {
    if (!window.confirm('작성 중인 핀과 총평이 사라져요. 건너뛸까요?')) return;
  }
  const prevId = state.target ? state.target.versionId : null;
  loadNext(prevId, { prevId });
});

$('retry-btn').addEventListener('click', () => loadNext(null));

renderNav();
loadNext(null);
