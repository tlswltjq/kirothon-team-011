// 내 이력서가 받은 평가 화면. 계약: design.md "요청·응답 JSON 계약".
import { api, imageUrl, el, errorText, getToken, getNickname } from './api.js';

// 평가자별 고정 팔레트 8색(라벨 순환).
const PALETTE = ['#d32f2f', '#1976d2', '#388e3c', '#f57c00', '#7b1fa2', '#00838f', '#c2185b', '#5d4037'];
const colorOf = (label) => PALETTE[(((label - 1) % 8) + 8) % 8];

const $ = (id) => document.getElementById(id);
const state = { resumeId: null, versionNo: null, blobUrls: [], seq: 0 };

function setStatus(msg, info) {
  const s = $('status');
  s.textContent = msg || '';
  s.className = 'my-status' + (info ? ' my-info' : '');
}

function revokeBlobs() {
  state.blobUrls.forEach((u) => URL.revokeObjectURL(u));
  state.blobUrls = [];
}

function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

function fmtTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? String(iso) : d.toLocaleString('ko-KR');
}

function fmtScore(v) {
  return v === null || v === undefined ? '평가 없음' : `평균 ${Number(v).toFixed(2)}점`;
}

function mark(listId, selector, pred) {
  for (const li of $(listId).children) li.classList.toggle('my-selected', pred(li));
}

async function loadList() {
  setStatus('불러오는 중...', true);
  try {
    const items = await api('/api/my/resumes');
    setStatus('');
    const ul = $('resume-list');
    clear(ul);
    $('resume-empty').hidden = items.length > 0;
    for (const r of items) {
      const btn = el('button', { text: '열기', attrs: { type: 'button' } });
      btn.addEventListener('click', () => openResume(r.resumeId));
      const li = el('li', { attrs: { 'data-id': r.resumeId } },
        el('strong', { text: r.title }),
        el('span', { text: `지원 직무: ${r.targetRole || '-'}` }),
        el('span', { text: `최신 v${r.latestVersionNo}` }),
        el('span', { text: `평가 ${r.reviewCount}개` }),
        btn);
      ul.appendChild(li);
    }
  } catch (e) {
    if (e.status === 401) {
      window.location.replace('index.html');
      return;
    }
    setStatus(errorText(e));
  }
}

async function openResume(resumeId) {
  const seq = ++state.seq;
  state.resumeId = resumeId;
  state.versionNo = null;
  revokeBlobs();
  $('detail-section').hidden = true;
  mark('resume-list', null, (li) => li.getAttribute('data-id') === String(resumeId));
  setStatus('불러오는 중...', true);
  try {
    const r = await api(`/api/my/resumes/${encodeURIComponent(resumeId)}`);
    if (seq !== state.seq) return;
    setStatus('');
    $('versions-section').hidden = false;
    $('versions-title').textContent = `버전: ${r.title}`;
    $('versions-meta').textContent = `지원 직무: ${r.targetRole || '-'}`;
    $('new-version-link').href = `upload.html?resumeId=${encodeURIComponent(resumeId)}`;
    const ul = $('version-list');
    clear(ul);
    for (const v of r.versions) {
      const published = v.status === 'PUBLISHED';
      const li = el('li', { attrs: { 'data-no': v.versionNo } },
        el('strong', { text: `v${v.versionNo}` }),
        el('span', { text: published ? '게시됨' : '초안', cls: 'my-badge ' + (published ? 'my-published' : 'my-draft') }),
        el('span', { text: published ? `게시 ${fmtTime(v.publishedAt)}` : '게시 전' }),
        el('span', { text: published ? fmtScore(v.averageScore) : '-' }),
        el('span', { text: `평가 ${v.reviewCount}개` }));
      if (published) {
        const btn = el('button', { text: '평가 보기', attrs: { type: 'button' } });
        btn.addEventListener('click', () => openVersion(resumeId, v));
        li.appendChild(btn);
      } else {
        li.appendChild(el('a', {
          text: '이어서 게시',
          attrs: { href: `upload.html?resumeId=${encodeURIComponent(resumeId)}&versionNo=${encodeURIComponent(v.versionNo)}` },
        }));
      }
      ul.appendChild(li);
    }
  } catch (e) {
    if (seq === state.seq) setStatus(errorText(e));
  }
}

function clearActive() {
  document.querySelectorAll('.my-pin-active').forEach((n) => n.classList.remove('my-pin-active'));
}

function activatePin(key) {
  clearActive();
  const target = document.querySelector(`.my-pin[data-key="${key}"]`);
  const item = document.querySelector(`.my-pinitem[data-key="${key}"]`);
  if (item) item.classList.add('my-pin-active');
  if (target) {
    target.classList.add('my-pin-active');
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
}

async function openVersion(resumeId, v) {
  const seq = ++state.seq;
  state.versionNo = v.versionNo;
  revokeBlobs();
  mark('version-list', null, (li) => li.getAttribute('data-no') === String(v.versionNo));
  $('detail-section').hidden = false;
  $('detail-title').textContent = `v${v.versionNo} 평가 결과`;
  const pages = $('pages');
  clear(pages);
  clear($('pin-list'));
  clear($('review-list'));
  setStatus('불러오는 중...', true);
  const base = `/api/my/resumes/${encodeURIComponent(resumeId)}/versions/${encodeURIComponent(v.versionNo)}`;
  try {
    const [reviews, urls] = await Promise.all([
      api(`${base}/reviews`),
      Promise.all(Array.from({ length: v.pageCount }, (_, i) => imageUrl(`${base}/pages/${i + 1}`))),
    ]);
    if (seq !== state.seq) {
      urls.forEach((u) => URL.revokeObjectURL(u));
      return;
    }
    state.blobUrls = urls;
    setStatus('');
    renderPages(urls, reviews);
    renderPinList(reviews);
    renderReviews(reviews);
  } catch (e) {
    if (seq === state.seq) setStatus(errorText(e));
  }
}

function renderPages(urls, reviews) {
  const pages = $('pages');
  const wraps = urls.map((u, i) => {
    const img = el('img', { attrs: { src: u, alt: `이력서 ${i + 1}쪽 (마스킹됨)` } });
    const wrap = el('div', { cls: 'my-page' }, img);
    pages.appendChild(el('p', { text: `${i + 1}쪽`, cls: 'my-pagelabel' }));
    pages.appendChild(wrap);
    return wrap;
  });
  for (const r of reviews) {
    for (const p of r.pins) {
      const wrap = wraps[p.page - 1];
      if (!wrap) continue;
      const key = `${r.reviewerLabel}-${p.seq}`;
      const pin = el('span', {
        text: key,
        cls: 'my-pin',
        attrs: { 'data-key': key, title: p.comment },
      });
      pin.style.left = `${p.x * 100}%`;
      pin.style.top = `${p.y * 100}%`;
      pin.style.backgroundColor = colorOf(r.reviewerLabel);
      pin.addEventListener('click', () => activatePin(key));
      wrap.appendChild(pin);
    }
  }
}

function renderPinList(reviews) {
  const ul = $('pin-list');
  const rows = [];
  for (const r of reviews) for (const p of r.pins) rows.push({ r, p });
  rows.sort((a, b) => a.r.reviewerLabel - b.r.reviewerLabel || a.p.seq - b.p.seq);
  $('pin-empty').hidden = rows.length > 0;
  for (const { r, p } of rows) {
    const key = `${r.reviewerLabel}-${p.seq}`;
    const chip = el('span', { text: key, cls: 'my-chip' });
    chip.style.backgroundColor = colorOf(r.reviewerLabel);
    const btn = el('button', { cls: 'my-pinitem', attrs: { type: 'button', 'data-key': key } },
      chip,
      el('span', { text: `평가자 ${r.reviewerLabel} · ${p.page}쪽 · 핀 ${p.seq}`, cls: 'my-pinmeta' }),
      el('span', { text: p.comment }));
    btn.addEventListener('click', () => activatePin(key));
    ul.appendChild(el('li', {}, btn));
  }
}

function renderReviews(reviews) {
  const ul = $('review-list');
  $('review-empty').hidden = reviews.length > 0;
  const sorted = [...reviews].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  for (const r of sorted) {
    const head = el('div', {},
      el('span', { text: `평가자 ${r.reviewerLabel}`, cls: 'my-score' }),
      el('span', { text: ` · 총점 ${r.totalScore}점` , cls: 'my-score' }));
    if (r.sample) head.appendChild(el('span', { text: '샘플', cls: 'my-badge my-sample' }));
    ul.appendChild(el('li', {},
      head,
      el('div', { text: fmtTime(r.createdAt), cls: 'my-time' }),
      el('p', { text: r.summary, cls: 'my-summary' })));
  }
}

function init() {
  if (!getToken()) {
    window.location.replace('index.html');
    return;
  }
  $('nickname').textContent = getNickname() || '';
  window.addEventListener('pagehide', revokeBlobs);
  loadList();
}

init();
