import { api, imageUrl, el, errorText, getToken, ApiError } from './api.js';
import { renderNav } from './nav.js';

const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const presetResumeId = params.get('resumeId');

const ERRORS = {
  UNAUTHENTICATED: '로그인 정보가 없습니다. 홈에서 닉네임으로 다시 시작해 주세요.',
  NOT_PDF: 'PDF 파일이 아닙니다. PDF만 올릴 수 있습니다.',
  FILE_TOO_LARGE: '파일이 5MB를 넘습니다. 더 작은 PDF를 올려 주세요.',
  TOO_MANY_PAGES: '10쪽을 넘는 PDF는 올릴 수 없습니다.',
  ENCRYPTED_PDF: '암호가 걸린 PDF입니다. 암호를 풀고 다시 올려 주세요.',
  NO_TEXT_LAYER: '글자를 선택할 수 없는 PDF(스캔본)입니다. 텍스트가 있는 PDF를 올려 주세요.',
  PDF_PARSE_FAILED: 'PDF를 읽을 수 없습니다. 다른 도구로 다시 저장한 PDF를 올려 주세요.',
  PDF_TOO_COMPLEX: 'PDF가 너무 복잡해 처리하지 못했습니다. 단순한 PDF로 다시 시도해 주세요.',
  UNSUPPORTED_PAGE_ROTATION: '회전된 페이지가 있는 PDF는 지원하지 않습니다. 회전을 풀고 다시 저장해 주세요.',
  PREVIEW_OUTDATED: '미리보기가 오래되었습니다. 최신 미리보기를 확인한 뒤 다시 게시해 주세요.',
  MASKING_INCOMPLETE: '마스킹 검증에 실패해 게시하지 않았습니다. 가릴 단어를 추가하고 미리보기를 다시 확인해 주세요.',
  DRAFT_SOURCE_EXPIRED: '초안 원본의 보관 기간(24시간)이 지났습니다. 같은 PDF를 다시 올려 주세요.',
  ALREADY_PUBLISHED: '이미 게시된 버전입니다. 게시된 버전은 바꿀 수 없습니다.',
  NOT_FOUND: '이력서를 찾을 수 없습니다.',
};

function message(err) {
  if (err instanceof ApiError) {
    if (err.code === 'VALIDATION_FAILED') {
      const f = Object.values(err.fields || {}).join(' / ');
      return `입력값을 확인해 주세요.${f ? ' ' + f : ''}`;
    }
    if (ERRORS[err.code]) return ERRORS[err.code];
    if (err.status === 413) return ERRORS.FILE_TOO_LARGE;
  }
  return errorText(err);
}

function show(host, text, kind) {
  host.replaceChildren(el('div', { text, cls: `msg ${kind || ''}` }));
}

const state = { resumeId: null, versionNo: null, version: null, urls: [], busy: false };

function base() {
  return `/api/my/resumes/${state.resumeId}/versions/${state.versionNo}`;
}

async function renderPages() {
  const host = $('pages');
  for (const u of state.urls) URL.revokeObjectURL(u);
  state.urls = [];
  host.replaceChildren();
  const count = state.version.preview.pageCount;
  const hash = state.version.preview.previewHash;
  for (let p = 1; p <= count; p++) {
    const img = el('img', { attrs: { alt: `${p}쪽 마스킹 미리보기` } });
    host.appendChild(el('div', {}, el('div', { text: `${p} / ${count}쪽`, cls: 'page-label' }),
      el('div', { cls: 'page-wrap' }, img)));
    try {
      const url = await imageUrl(`${base()}/pages/${p}`);
      if (state.version.preview.previewHash !== hash) { URL.revokeObjectURL(url); return; }
      state.urls.push(url);
      img.src = url;
    } catch (err) {
      host.appendChild(el('div', { text: `${p}쪽 이미지를 불러오지 못했습니다. ${message(err)}`, cls: 'msg error' }));
    }
  }
}

const TYPE_LABELS = { EMAIL: '이메일', PHONE: '전화번호', URL: 'URL', TERM: '지정 단어', IMAGE: '이미지' };

function renderVersion() {
  const v = state.version;
  const pv = v.preview || {};
  $('preview-card').classList.remove('hidden');
  const counts = $('summary');
  counts.replaceChildren(el('p', { text: `총 ${pv.pageCount}쪽`, cls: 'muted' }));
  const box = el('div', { cls: 'counts' });
  for (const [key, label] of Object.entries(TYPE_LABELS)) {
    box.appendChild(el('div', { text: `${label} ${(pv.counts && pv.counts[key]) || 0}곳` }));
  }
  counts.appendChild(box);

  const warns = $('warnings');
  warns.replaceChildren();
  for (const w of pv.warnings || []) {
    const text = typeof w === 'string' ? w : (w.message || w.code || '');
    warns.appendChild(el('div', { text, cls: 'msg warn' }));
  }

  const terms = $('terms');
  terms.replaceChildren();
  const unmatched = new Set(pv.unmatchedTerms || []);
  for (const t of v.maskTerms || []) {
    const chip = el('span', { cls: 'chip' + (unmatched.has(t) ? ' nomatch' : '') }, el('span', { text: t }));
    const rm = el('button', { text: '삭제', attrs: { type: 'button', 'aria-label': `${t} 삭제` } });
    rm.addEventListener('click', () => saveTerms((state.version.maskTerms || []).filter((x) => x !== t)));
    chip.appendChild(rm);
    terms.appendChild(chip);
  }
  const um = $('unmatched');
  um.replaceChildren();
  if (unmatched.size) {
    show(um, `일치 없음: ${[...unmatched].join(', ')} (줄 텍스트에서 한 번도 찾지 못했습니다. 오타를 확인하세요.)`, 'warn');
  }

  const nc = $('name-candidate');
  nc.replaceChildren();
  if (pv.nameCandidate && !(v.maskTerms || []).includes(pv.nameCandidate)) {
    const btn = el('button', { text: '가릴 단어에 추가', cls: 'secondary', attrs: { type: 'button' } });
    btn.addEventListener('click', () => saveTerms([...(state.version.maskTerms || []), pv.nameCandidate]));
    nc.appendChild(el('div', { cls: 'msg' },
      el('span', { text: `이름 후보: "${pv.nameCandidate}" ` }), btn));
  }

  $('confirm').checked = false;
  $('publish-btn').disabled = true;
  $('publish-msg').replaceChildren();
  if (v.status === 'PUBLISHED') lockPublished();
  renderPages();
}

function lockPublished() {
  $('publish-btn').disabled = true;
  $('confirm').disabled = true;
  $('term-btn').disabled = true;
  $('term').disabled = true;
}

async function saveTerms(terms) {
  if (state.busy) return;
  state.busy = true;
  const msg = $('term-msg');
  msg.replaceChildren();
  try {
    state.version = await api(`${base()}/mask-terms`, { method: 'PUT', body: { terms } });
    renderVersion();
  } catch (err) {
    show(msg, message(err), 'error');
  } finally {
    state.busy = false;
  }
}

function afterUpload(res) {
  state.resumeId = res.resumeId;
  state.versionNo = res.versionNo;
  state.version = res.version;
  $('top-msg').replaceChildren();
  $('page-title').textContent = `미리보기 (v${res.versionNo})`;
  renderVersion();
  $('preview-card').scrollIntoView();
}

function init() {
  renderNav();
  if (!getToken()) {
    location.replace('/index.html');
    return;
  }
  if (presetResumeId) {
    $('meta-fields').classList.add('hidden');
    $('title').required = false;
    $('targetRole').required = false;
    $('page-title').textContent = '새 버전 업로드';
    $('upload-heading').textContent = '수정한 PDF 올리기';
    $('upload-btn').textContent = '새 버전 올리고 미리보기 만들기';
  }
}

$('upload-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (state.busy) return;
  const file = $('file').files[0];
  if (!file) { show($('top-msg'), 'PDF 파일을 선택해 주세요.', 'error'); return; }
  const form = new FormData();
  let path;
  if (presetResumeId) {
    path = `/api/my/resumes/${encodeURIComponent(presetResumeId)}/versions`;
  } else {
    const title = $('title').value.trim();
    const role = $('targetRole').value.trim();
    if (!title || !role) { show($('top-msg'), '제목과 지원 직무를 입력해 주세요.', 'error'); return; }
    form.append('title', title);
    form.append('targetRole', role);
    path = '/api/my/resumes';
  }
  form.append('file', file);
  state.busy = true;
  $('upload-btn').disabled = true;
  show($('top-msg'), '업로드하고 마스킹 미리보기를 만드는 중입니다...');
  try {
    afterUpload(await api(path, { method: 'POST', body: form }));
  } catch (err) {
    show($('top-msg'), message(err), 'error');
    if (err instanceof ApiError && err.status === 401) setTimeout(() => location.replace('/index.html'), 1500);
  } finally {
    state.busy = false;
    $('upload-btn').disabled = false;
  }
});

$('term-form').addEventListener('submit', (e) => {
  e.preventDefault();
  const t = $('term').value.trim();
  const list = state.version.maskTerms || [];
  if (t.length < 2 || t.length > 30) { show($('term-msg'), '가릴 단어는 2~30자여야 합니다.', 'error'); return; }
  if (list.includes(t)) { show($('term-msg'), '이미 추가한 단어입니다.', 'error'); return; }
  if (list.length >= 50) { show($('term-msg'), '가릴 단어는 최대 50개입니다.', 'error'); return; }
  $('term').value = '';
  saveTerms([...list, t]);
});

$('confirm').addEventListener('change', () => {
  $('publish-btn').disabled = !$('confirm').checked || state.version.status === 'PUBLISHED';
});

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
    msg.replaceChildren(el('div', { cls: 'msg ok' },
      el('span', { text: `v${res.versionNo}을 게시했습니다. 이제 다른 사용자가 평가할 수 있습니다. ` }),
      el('a', { text: '내 이력서 보기', cls: 'btn', attrs: { href: '/my.html' } })));
  } catch (err) {
    show(msg, message(err), 'error');
    $('publish-btn').disabled = !$('confirm').checked;
  } finally {
    state.busy = false;
  }
});

init();
