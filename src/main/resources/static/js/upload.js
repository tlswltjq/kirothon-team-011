import { api, el, errorText, ensureAnon, loadPageImage, ApiError } from './api.js';
import { renderNav } from './nav.js';

const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const presetResumeId = params.get('resumeId');
const presetVersionNo = params.get('versionNo');

const ERRORS = {
  UNAUTHENTICATED: '이 브라우저의 익명 열쇠가 사라져 초안을 이어 갈 수 없어요. PDF를 다시 올려 주세요.',
  NOT_PDF: 'PDF 파일이 아니에요. PDF만 올릴 수 있어요.',
  FILE_TOO_LARGE: '파일이 5MB를 넘어요. 더 작은 PDF를 올려 주세요.',
  TOO_MANY_PAGES: '10쪽을 넘는 PDF는 올릴 수 없어요.',
  ENCRYPTED_PDF: '암호가 걸린 PDF예요. 암호를 풀고 다시 올려 주세요.',
  NO_TEXT_LAYER: '글자를 선택할 수 없는 PDF(스캔본)예요. 텍스트가 있는 PDF를 올려 주세요.',
  PDF_PARSE_FAILED: 'PDF를 읽을 수 없어요. 다른 도구로 다시 저장한 PDF를 올려 주세요.',
  PDF_TOO_COMPLEX: 'PDF가 너무 복잡해 처리하지 못했어요. 단순한 PDF로 다시 시도해 주세요.',
  UNSUPPORTED_PAGE_ROTATION: '회전된 페이지가 있는 PDF는 지원하지 않아요. 회전을 풀고 다시 저장해 주세요.',
  PREVIEW_OUTDATED: '가린 결과가 바뀌었어요. 최신 이미지를 다시 확인해 주세요.',
  MASKING_INCOMPLETE: '가림 검증에 실패해 게시하지 않았어요. 가릴 단어를 다시 저장해 주세요.',
  DRAFT_SOURCE_EXPIRED: '초안 원본 보관 시간(24시간)이 지나 지웠어요. 같은 PDF를 다시 올려 주세요.',
  ALREADY_PUBLISHED: '이미 게시된 버전이에요. 게시된 버전은 바꿀 수 없어요.',
  NOT_FOUND: '이력서를 찾을 수 없어요.',
};
const ROLE_ERROR = '지원 직무에는 이메일·전화번호·링크를 넣을 수 없어요. 평가자에게 그대로 보여요.';

function message(err) {
  if (err instanceof ApiError) {
    if (err.code === 'VALIDATION_FAILED') {
      const f = Object.values(err.fields || {}).join(' / ');
      return `입력값을 확인해 주세요.${f ? ' ' + f : ''}`;
    }
    if (ERRORS[err.code]) return ERRORS[err.code];
    if (err.status === 413) return ERRORS.FILE_TOO_LARGE;
    if (err.status === 401) return ERRORS.UNAUTHENTICATED;
  }
  return errorText(err);
}

function show(host, text, kind) {
  host.replaceChildren(el('div', { text, cls: `msg ${kind || ''}` }));
}

const state = {
  resumeId: presetResumeId || null, versionNo: null, version: null, urls: [],
  busy: false, rebuilding: false, seen: false, fileName: '', role: '',
};

function base() {
  return `/api/my/resumes/${state.resumeId}/versions/${state.versionNo}`;
}

function setStep(n) {
  for (const li of $('steps').children) {
    const k = Number(li.dataset.step);
    li.classList.toggle('current', k === n);
    li.classList.toggle('done', k < n);
  }
}

// 체크·게시 버튼 활성 여부는 이 함수 한 곳에서 계산한다.
function imagesOk() {
  const count = state.version && state.version.preview ? state.version.preview.pageCount : 0;
  const wraps = $('pages').querySelectorAll('.page-wrap');
  if (!count || wraps.length !== count) return false;
  return [...wraps].every((w) => w.querySelector('img') && !w.querySelector('button'));
}

function updateGate() {
  const v = state.version;
  const published = !v || v.status === 'PUBLISHED';
  const ok = !published && !state.busy && !state.rebuilding && state.seen && imagesOk();
  const c = $('confirm');
  c.disabled = !ok;
  if (!ok) c.checked = false;
  $('publish-btn').disabled = !(ok && c.checked);
  let hint = '';
  if (!published) {
    if (state.rebuilding) hint = '다시 가리는 중이에요. 끝나면 체크할 수 있어요.';
    else if (!state.seen) hint = '마지막 쪽 끝까지 내려 보면 체크할 수 있어요.';
    else if (!imagesOk()) hint = '모든 쪽 이미지가 뜨면 체크할 수 있어요.';
  }
  $('confirm-hint').textContent = hint;
}

new MutationObserver(updateGate).observe($('pages'), {
  childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'class'],
});

let endObserver = null;
function watchEnd() {
  state.seen = false;
  if (endObserver) endObserver.disconnect();
  if (!('IntersectionObserver' in window)) { state.seen = true; return; }
  endObserver = new IntersectionObserver((entries) => {
    if (entries.some((e) => e.isIntersecting)) {
      state.seen = true;
      endObserver.disconnect();
      updateGate();
    }
  });
  endObserver.observe($('pages-end'));
}

function renderPages() {
  const host = $('pages');
  for (const u of state.urls) URL.revokeObjectURL(u);
  state.urls = [];
  host.replaceChildren();
  const count = state.version.preview.pageCount;
  const hash = state.version.preview.previewHash;
  const jobs = [];
  for (let p = 1; p <= count; p++) {
    const wrap = el('div', { cls: 'page-wrap' });
    host.appendChild(el('div', {}, el('div', { text: `${p} / ${count}쪽`, cls: 'page-label' }), wrap));
    const track = (url) => {
      if (state.version.preview.previewHash !== hash) { URL.revokeObjectURL(url); return; }
      state.urls.push(url);
    };
    jobs.push(Promise.resolve(loadPageImage(wrap, `${base()}/pages/${p}`, `${p}쪽 마스킹 미리보기`, track))
      .catch(() => {}));
  }
  watchEnd();
  Promise.allSettled(jobs).then(updateGate);
}

const TYPE_LABELS = { EMAIL: '이메일', PHONE: '전화번호', URL: '링크', IMAGE: '사진', TERM: '지정 단어' };

function renderVersion() {
  const v = state.version;
  const pv = v.preview || {};
  $('preview-card').classList.remove('hidden');
  const counts = $('summary');
  counts.replaceChildren();
  for (const [key, label] of Object.entries(TYPE_LABELS)) {
    const n = (pv.counts && pv.counts[key]) || 0;
    counts.appendChild(el('span', { text: `${label} ${n}`, cls: 'count-chip' + (n ? '' : ' zero') }));
  }

  const warns = $('warnings');
  warns.replaceChildren();
  for (const w of pv.warnings || []) {
    const text = typeof w === 'string' ? w : (w.message || w.code || '');
    warns.appendChild(el('div', { text, cls: 'msg warn' }));
  }

  const terms = $('terms');
  terms.replaceChildren();
  const unmatched = new Set(pv.unmatchedTerms || []);
  const list = v.maskTerms || [];
  for (const t of list) {
    const chip = el('span', { cls: 'chip' + (unmatched.has(t) ? ' nomatch' : '') }, el('span', { text: t }));
    if (unmatched.has(t)) chip.appendChild(el('small', { text: '찾지 못함' }));
    const rm = el('button', { text: '삭제', attrs: { type: 'button', 'aria-label': `${t} 삭제` } });
    rm.addEventListener('click', () => saveTerms((state.version.maskTerms || []).filter((x) => x !== t)));
    chip.appendChild(rm);
    terms.appendChild(chip);
  }
  const um = $('unmatched');
  um.replaceChildren();
  if (unmatched.size) show(um, '오타가 없는지 확인하세요.', 'warn');

  const nc = $('name-candidate');
  nc.replaceChildren();
  if (list.length === 0 && pv.nameCandidate) {
    const words = [...new Set(String(pv.nameCandidate).split(/\s+/).filter((w) => w.length >= 2))];
    for (const w of words) {
      const btn = el('button', { text: `+ ${w}`, cls: 'secondary', attrs: { type: 'button' } });
      btn.addEventListener('click', () => saveTerms([...(state.version.maskTerms || []), w]));
      nc.appendChild(btn);
    }
  }

  $('confirm').checked = false;
  $('publish-msg').replaceChildren();
  if (v.status === 'PUBLISHED') lockPublished();
  updateGate();
  renderPages();
}

function lockPublished() {
  $('term-btn').disabled = true;
  $('term').disabled = true;
  updateGate();
}

async function saveTerms(terms) {
  if (state.busy) return;
  state.busy = true;
  state.rebuilding = true;
  updateGate();
  const msg = $('term-msg');
  show(msg, '다시 가리는 중…');
  try {
    state.version = await api(`${base()}/mask-terms`, { method: 'PUT', body: { terms } });
    msg.replaceChildren();
    state.rebuilding = false;
    state.busy = false;
    renderVersion();
  } catch (err) {
    show(msg, message(err), 'error');
  } finally {
    state.busy = false;
    state.rebuilding = false;
    updateGate();
  }
}

// 1단계 접기/펼치기
function collapseUpload() {
  $('summary-text').textContent = `${state.fileName} · ${state.role || '지원 직무'} · v${state.versionNo} 초안`;
  $('upload-summary').classList.remove('hidden');
  $('upload-body').classList.add('hidden');
  $('upload-card').classList.add('collapsed');
}

function expandUpload(headingText) {
  $('upload-summary').classList.add('hidden');
  $('upload-body').classList.remove('hidden');
  $('upload-card').classList.remove('collapsed', 'hidden');
  if (headingText) $('upload-heading').textContent = headingText;
  setStep(1);
}

function useExistingResume() {
  $('meta-fields').classList.add('hidden');
  $('title').required = false;
  $('targetRole').required = false;
}

function useNewResume() {
  $('meta-fields').classList.remove('hidden');
  $('title').required = true;
  $('targetRole').required = true;
}

function afterUpload(res) {
  state.resumeId = res.resumeId;
  state.versionNo = res.versionNo;
  state.version = res.version;
  state.fileName = $('file').files[0] ? $('file').files[0].name : state.fileName;
  $('top-msg').replaceChildren();
  $('file-msg').replaceChildren();
  $('role-msg').replaceChildren();
  $('file').value = '';
  useExistingResume();
  collapseUpload();
  setStep(2);
  $('done-card').classList.add('hidden');
  renderVersion();
}

async function init() {
  renderNav();
  setStep(1);
  if (presetResumeId) {
    useExistingResume();
    $('page-title').textContent = '새 버전 올리기';
    $('upload-heading').textContent = '수정한 PDF 올리기';
  }
  try {
    await ensureAnon();
  } catch (err) {
    show($('top-msg'), '연결에 실패했어요. 잠시 뒤 다시 시도해 주세요.', 'error');
    return;
  }
  if (presetResumeId) {
    try {
      const r = await api(`/api/my/resumes/${encodeURIComponent(presetResumeId)}`);
      state.role = r.targetRole || '';
      $('upload-heading').textContent = `‘${r.title}’의 새 버전을 올려요`;
    } catch (err) {
      show($('top-msg'), message(err), 'error');
      return;
    }
    if (presetVersionNo && /^\d+$/.test(presetVersionNo)) {
      try {
        const v = await api(`/api/my/resumes/${encodeURIComponent(presetResumeId)}/versions/${presetVersionNo}`);
        if (v && v.status !== 'PUBLISHED' && v.preview) {
          state.versionNo = Number(presetVersionNo);
          state.version = v;
          state.fileName = '이어서 게시할 초안';
          collapseUpload();
          setStep(2);
          renderVersion();
        }
      } catch (err) {
        // 초안을 못 불러오면 기존처럼 새 PDF 업로드 화면을 그대로 둔다.
      }
    }
    return;
  }
  try {
    const items = await api('/api/my/resumes');
    if (items && items.length > 0) {
      const r = items[0];
      const box = $('existing-hint');
      box.replaceChildren(
        el('span', { text: `고친 이력서라면 ‘${r.title}’의 새 버전으로 올려야 점수 변화를 비교할 수 있어요. ` }),
        el('a', { text: '새 버전으로 올리기 →', attrs: { href: `/upload.html?resumeId=${encodeURIComponent(r.resumeId)}` } }));
      box.classList.remove('hidden');
    }
  } catch (err) {
    // 안내는 선택 사항이라 실패해도 무시한다.
  }
}

$('file').addEventListener('change', () => {
  const f = $('file').files[0];
  if (f && !state.resumeId && !$('title').value.trim()) {
    $('title').value = f.name.replace(/\.pdf$/i, '').slice(0, 60);
  }
});

$('replace-btn').addEventListener('click', () => expandUpload('다른 PDF 올리기'));

$('upload-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (state.busy) return;
  $('file-msg').replaceChildren();
  $('role-msg').replaceChildren();
  const file = $('file').files[0];
  if (!file) { show($('file-msg'), 'PDF 파일을 선택해 주세요.', 'error'); return; }
  const form = new FormData();
  let path;
  if (state.resumeId) {
    path = `/api/my/resumes/${encodeURIComponent(state.resumeId)}/versions`;
  } else {
    const title = $('title').value.trim();
    const role = $('targetRole').value.trim();
    if (!role) { show($('role-msg'), '지원 직무를 입력해 주세요.', 'error'); return; }
    if (!title) { show($('file-msg'), '제목을 입력해 주세요.', 'error'); return; }
    form.append('title', title);
    form.append('targetRole', role);
    state.role = role;
    path = '/api/my/resumes';
  }
  form.append('file', file);
  state.busy = true;
  const btn = $('upload-btn');
  const label = btn.textContent;
  btn.disabled = true;
  btn.textContent = '가리는 중…';
  $('upload-loading').classList.remove('hidden');
  try {
    await ensureAnon();
    afterUpload(await api(path, { method: 'POST', body: form }));
  } catch (err) {
    if (err instanceof ApiError && err.code === 'VALIDATION_FAILED' && err.fields && err.fields.targetRole) {
      show($('role-msg'), ROLE_ERROR, 'error');
    } else {
      show($('file-msg'), message(err), 'error');
    }
    if (err instanceof ApiError && err.status === 401) {
      state.resumeId = null;
      useNewResume();
    }
  } finally {
    state.busy = false;
    btn.disabled = false;
    btn.textContent = label;
    $('upload-loading').classList.add('hidden');
  }
});

$('term-form').addEventListener('submit', (e) => {
  e.preventDefault();
  if (!state.version) return;
  const t = $('term').value.trim();
  const list = state.version.maskTerms || [];
  if (t.length < 2 || t.length > 30) { show($('term-msg'), '가릴 단어는 2~30자여야 해요.', 'error'); return; }
  if (list.includes(t)) { show($('term-msg'), '이미 추가한 단어예요.', 'error'); return; }
  if (list.length >= 50) { show($('term-msg'), '가릴 단어는 최대 50개예요.', 'error'); return; }
  $('term').value = '';
  saveTerms([...list, t]);
});

$('confirm').addEventListener('change', updateGate);

$('publish-btn').addEventListener('click', async () => {
  if (state.busy || !$('confirm').checked) return;
  state.busy = true;
  $('publish-btn').disabled = true;
  const msg = $('publish-msg');
  msg.replaceChildren();
  try {
    const res = await api(`${base()}/publish`, {
      method: 'POST',
      body: { previewHash: state.version.preview.previewHash, confirmed: true },
    });
    state.version.status = res.status;
    lockPublished();
    $('preview-card').classList.add('hidden');
    $('done-title').textContent = `v${res.versionNo}을 게시했어요`;
    $('done-my').setAttribute('href', `/my.html?resumeId=${encodeURIComponent(state.resumeId)}`);
    $('done-card').classList.remove('hidden');
    $('upload-card').classList.add('hidden');
    setStep(3);
    window.scrollTo(0, 0);
  } catch (err) {
    $('confirm').checked = false;
    show(msg, message(err), 'error');
    if (err instanceof ApiError && (err.code === 'PREVIEW_OUTDATED' || err.code === 'MASKING_INCOMPLETE')) {
      state.rebuilding = true;
      updateGate();
      try {
        state.version = await api(base());
        renderVersion();
      } catch (e2) {
        show(msg, message(e2), 'error');
      } finally {
        state.rebuilding = false;
      }
    }
    if (err instanceof ApiError && (err.status === 410 || err.status === 401)) {
      $('preview-card').classList.add('hidden');
      expandUpload('PDF 다시 올리기');
      if (err.status === 401) { state.resumeId = null; useNewResume(); }
      show($('file-msg'), message(err), 'error');
    }
  } finally {
    state.busy = false;
    updateGate();
  }
});

init();
