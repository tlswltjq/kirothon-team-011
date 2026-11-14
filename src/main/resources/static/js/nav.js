// 공통 상단 내비. <header class="nav" id="nav"> 안에 그린다.
import { el, getNickname } from './api.js';

const LINKS = [
  ['index.html', '홈'],
  ['upload.html', '업로드'],
  ['review.html', '평가하기'],
  ['my.html', '내 이력서'],
];

export function renderNav() {
  const host = document.getElementById('nav');
  if (!host) return;
  host.classList.add('nav');
  const current = location.pathname.split('/').pop() || 'index.html';
  const nav = el('nav', {}, el('span', { text: '익명 이력서 평가', cls: 'brand' }));
  for (const [href, label] of LINKS) {
    nav.appendChild(el('a', { text: label, cls: href === current ? 'current' : '', attrs: { href } }));
  }
  const nick = getNickname();
  if (nick) nav.appendChild(el('span', { text: `닉네임: ${nick}`, cls: 'who' }));
  host.replaceChildren(nav);
}
