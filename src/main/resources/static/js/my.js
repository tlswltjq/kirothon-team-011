// 내 이력서가 받은 평가 화면. 계약: design.md "요청·응답 JSON 계약".
import { api, el, errorText, getToken, loadPageImage } from './api.js';
import { renderNav } from './nav.js';

// 평가자별 고정 팔레트 8색(라벨 순환).
// 흰 글자 대비 4.5:1 이상인 색만 쓴다. 첫 색은 서비스 보라(--purple)와 같다.
const PALETTE = ['#7c3aed', '#1d4ed8', '#15803d', '#b45309', '#be185d', '#0e7490', '#b91c1c', '#5b21b6'];
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

const SVGNS = 'http://www.w3.org/2000/svg';

function svg(tag, attrs, text) {
  const n = document.createElementNS(SVGNS, tag);
  for (const [k, v] of Object.entries(attrs || {})) n.setAttribute(k, String(v));
  if (text !== undefined) n.textContent = text;
  return n;
}

function show(id, on) {
  $(id).hidden = !on;
}

function showEmpty() {
  setStatus('');
  show('empty-state', true);
  show('error-state', false);
  show('list-section', false);
  show('versions-section', false);
  show('detail-section', false);
  show('trend-card', false);
}

function showError(e) {
  setStatus('');
  show('error-state', true);
  show('empty-state', false);
  const msg = errorText(e);
  if (msg) setStatus(msg);
}

async function loadList() {
  setStatus('불러오는 중이에요…', true);
  show('error-state', false);
  show('empty-state', false);
  if (!getToken()) {
    showEmpty();
    return;
  }
  try {
    const items = await api('/api/my/resumes');
    setStatus('');
    if (!items.length) {
      showEmpty();
      return;
    }
    show('list-section', true);
    const ul = $('resume-list');
    clear(ul);
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
    const wanted = new URLSearchParams(window.location.search).get('resumeId');
    const hit = wanted && items.find((r) => String(r.resumeId) === wanted);
    if (hit) await openResume(hit.resumeId, true);
    else if (items.length === 1) await openResume(items[0].resumeId, true);
  } catch (e) {
    if (e.status === 401) showEmpty();
    else showError(e);
  }
}

function publishedOf(versions) {
  return versions.filter((v) => v.status === 'PUBLISHED').sort((a, b) => a.versionNo - b.versionNo);
}

// 숫자 받침에 맞춘 목적격 조사(1·3·6·7·8·10은 '을').
const objParticle = (n) => ([0, 1, 3, 6, 7, 8].includes(n % 10) ? '을' : '를');

const hasAvg = (v) => v && v.averageScore !== null && v.averageScore !== undefined;

function renderChart(pub) {
  const box = $('trend-chart');
  clear(box);
  const W = 320;
  const H = 140;
  const left = 32;
  const right = W - 20;
  const top = 20;
  const bottom = 108;
  const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': '버전별 평균 총점 추이' });
  const yOf = (sc) => bottom - ((sc - 1) / 4) * (bottom - top);
  for (let sc = 1; sc <= 5; sc++) {
    root.appendChild(svg('line', { x1: left, x2: right, y1: yOf(sc), y2: yOf(sc), class: 'my-grid' }));
    root.appendChild(svg('text', { x: left - 6, y: yOf(sc) + 3, 'text-anchor': 'end', class: 'my-axis' }, String(sc)));
  }
  const n = pub.length;
  const xOf = (i) => (n === 1 ? (left + right) / 2 : left + 12 + (i * (right - left - 24)) / (n - 1));
  for (let i = 0; i < n; i++) {
    root.appendChild(svg('text', { x: xOf(i), y: bottom + 18, 'text-anchor': 'middle', class: 'my-axis' }, `v${pub[i].versionNo}`));
    if (i > 0 && hasAvg(pub[i]) && hasAvg(pub[i - 1])) {
      root.appendChild(svg('line', {
        x1: xOf(i - 1), y1: yOf(pub[i - 1].averageScore), x2: xOf(i), y2: yOf(pub[i].averageScore), class: 'my-line',
      }));
    }
  }
  for (let i = 0; i < n; i++) {
    if (!hasAvg(pub[i])) continue;
    const cx = xOf(i);
    const cy = yOf(pub[i].averageScore);
    root.appendChild(svg('circle', { cx, cy, r: 5, class: 'my-dot' + (i === n - 1 ? ' latest' : '') }));
    root.appendChild(svg('text', { x: cx, y: cy - 9, 'text-anchor': 'middle', class: 'my-val' }, Number(pub[i].averageScore).toFixed(2)));
  }
  box.appendChild(root);
}

function renderTrend(r) {
  const pub = publishedOf(r.versions);
  show('trend-card', pub.length > 0);
  if (!pub.length) return;
  $('trend-title').textContent = r.title;
  $('trend-role').textContent = `지원 직무: ${r.targetRole || '-'}`;
  const last = pub[pub.length - 1];
  const prev = pub.length > 1 ? pub[pub.length - 2] : null;
  const badge = $('trend-badge');
  badge.hidden = true;
  $('trend-wait').hidden = hasAvg(last);
  if (!prev) {
    $('trend-summary').textContent = hasAvg(last) ? `v${last.versionNo} 평균 ${Number(last.averageScore).toFixed(2)}점` : `v${last.versionNo} 평가 없음`;
    $('trend-chart').hidden = true;
    clear($('trend-chart'));
    show('trend-single', true);
    $('trend-single-text').textContent = hasAvg(last)
      ? `평균 ${Number(last.averageScore).toFixed(2)}점 · 평가 ${last.reviewCount}개`
      : '아직 받은 평가가 없어요.';
    $('trend-single-hint').textContent = `v${last.versionNo + 1}${objParticle(last.versionNo + 1)} 올리면 여기서 점수 변화를 비교해요.`;
    $('trend-latest-note').hidden = true;
    $('trend-upload').href = `upload.html?resumeId=${encodeURIComponent(r.resumeId)}`;
    $('trend-upload').textContent = `고친 이력서 올리기 (v${last.versionNo + 1})`;
    return;
  }
  show('trend-single', false);
  $('trend-chart').hidden = false;
  const fmt = (v) => (hasAvg(v) ? `${Number(v.averageScore).toFixed(2)}점` : '평가 없음');
  $('trend-summary').textContent = `v${prev.versionNo} ${fmt(prev)} → v${last.versionNo} ${fmt(last)}`;
  if (hasAvg(last) && hasAvg(prev)) {
    const d = Math.round((last.averageScore - prev.averageScore) * 100) / 100;
    badge.hidden = false;
    badge.className = 'my-delta ' + (d > 0 ? 'up' : d < 0 ? 'down' : '');
    badge.textContent = d === 0 ? '±0.00' : `${d > 0 ? '+' : '-'}${Math.abs(d).toFixed(2)}`;
  }
  const note = $('trend-latest-note');
  note.hidden = !(last.reviewCount > 0);
  note.textContent = `v${last.versionNo}에 평가 ${last.reviewCount}개가 쌓였어요.`;
  renderChart(pub);
}

async function openResume(resumeId, autoOpen) {
  const seq = ++state.seq;
  state.resumeId = resumeId;
  state.versionNo = null;
  revokeBlobs();
  $('detail-section').hidden = true;
  mark('resume-list', null, (li) => li.getAttribute('data-id') === String(resumeId));
  setStatus('불러오는 중이에요…', true);
  try {
    const r = await api(`/api/my/resumes/${encodeURIComponent(resumeId)}`);
    if (seq !== state.seq) return;
    setStatus('');
    state.versions = r.versions;
    $('versions-section').hidden = false;
    $('versions-title').textContent = `버전: ${r.title}`;
    $('versions-meta').textContent = `지원 직무: ${r.targetRole || '-'}`;
    const link = $('new-version-link');
    link.href = `upload.html?resumeId=${encodeURIComponent(resumeId)}`;
    const maxNo = Math.max(0, ...r.versions.map((v) => v.versionNo));
    link.textContent = `고친 이력서 올리기 (v${maxNo + 1})`;
    renderTrend({ ...r, resumeId });
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
    if (autoOpen) {
      const pub = publishedOf(r.versions);
      if (pub.length) await openVersion(resumeId, pub[pub.length - 1]);
    }
  } catch (e) {
    if (seq !== state.seq) return;
    if (e.status === 401) showEmpty();
    else showError(e);
  }
}

function prefersReducedMotion() {
  return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
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
    target.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'center' });
  }
}

async function openVersion(resumeId, v) {
  const seq = ++state.seq;
  state.versionNo = v.versionNo;
  revokeBlobs();
  mark('version-list', null, (li) => li.getAttribute('data-no') === String(v.versionNo));
  $('detail-section').hidden = false;
  const avg = hasAvg(v) ? `평균 ${Number(v.averageScore).toFixed(2)}점 · ` : '';
  $('detail-title').textContent = `v${v.versionNo} 평가 결과 · ${avg}평가 ${v.reviewCount}개`;
  clear($('pages'));
  clear($('pin-list'));
  clear($('review-list'));
  $('pin-empty').hidden = true;
  $('review-empty').hidden = true;
  const base = `/api/my/resumes/${encodeURIComponent(resumeId)}/versions/${encodeURIComponent(v.versionNo)}`;
  const wraps = buildPages(v.pageCount, base, seq);
  setStatus('평가를 불러오는 중이에요…', true);
  try {
    const reviews = await api(`${base}/reviews`);
    if (seq !== state.seq) return;
    setStatus('');
    renderPins(wraps, reviews);
    renderPinList(reviews);
    renderReviews(reviews);
  } catch (e) {
    if (seq !== state.seq) return;
    setStatus('');
    const retry = el('button', { text: '다시 시도', cls: 'my-retry', attrs: { type: 'button' } });
    retry.addEventListener('click', () => openVersion(resumeId, v));
    $('review-list').appendChild(el('li', {}, el('span', { text: `평가를 불러오지 못했어요. ${errorText(e)}` }), retry));
  }
}

function buildPages(count, base, seq) {
  const pages = $('pages');
  const wraps = [];
  for (let i = 0; i < count; i++) {
    const imgBox = el('div', { cls: 'my-imgbox' });
    const overlay = el('div', { cls: 'my-overlay' });
    const wrap = el('div', { cls: 'my-page' }, imgBox, overlay);
    pages.appendChild(el('p', { text: `${i + 1}쪽`, cls: 'my-pagelabel' }));
    pages.appendChild(wrap);
    wraps.push(overlay);
    const track = (u) => {
      if (typeof u !== 'string') return;
      if (seq !== state.seq) URL.revokeObjectURL(u);
      else state.blobUrls.push(u);
    };
    Promise.resolve(loadPageImage(imgBox, `${base}/pages/${i + 1}`, `이력서 ${i + 1}쪽 (가림 처리됨)`, track))
      .catch(() => {});
  }
  return wraps;
}

function renderPins(wraps, reviews) {
  for (const r of reviews) {
    for (const p of r.pins) {
      const wrap = wraps[p.page - 1];
      if (!wrap) continue;
      const key = `${r.reviewerLabel}-${p.seq}`;
      const pin = el('button', {
        text: key,
        cls: 'my-pin',
        attrs: {
          type: 'button', 'data-key': key, title: p.comment,
          'aria-label': `평가자 ${r.reviewerLabel}의 ${p.seq}번 핀, ${p.page}쪽`,
        },
      });
      pin.style.setProperty('left', `${p.x * 100}%`);
      pin.style.setProperty('top', `${p.y * 100}%`);
      pin.style.setProperty('background-color', colorOf(r.reviewerLabel));
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
  renderNav();
  $('retry-btn').addEventListener('click', loadList);
  window.addEventListener('pagehide', revokeBlobs);
  loadList();
}

init();
