// 공통 상단 내비. <header class="nav" id="nav"> 안에 그린다.
import { el } from './api.js';

const LINKS = [
  ['upload.html', '평가받기'],
  ['review.html', '평가하기'],
  ['my.html', '내 이력서'],
];

export function renderNav() {
  const host = document.getElementById('nav');
  if (!host) return;
  host.classList.add('nav');
  const current = location.pathname.split('/').pop() || 'index.html';
  const nav = el('nav', {}, el('a', { cls: 'brand', attrs: { href: 'index.html' } }, el('span', { cls: 'dot', attrs: { 'aria-hidden': 'true' } }), el('span', { text: '이게 다 너 잘되라고 하는 소리야' })));
  for (const [href, label] of LINKS) {
    nav.appendChild(el('a', { text: label, cls: href === current ? 'current' : '', attrs: { href } }));
  }
  host.replaceChildren(nav);
}
