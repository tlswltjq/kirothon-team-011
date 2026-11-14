// 데모 녹화 스크립트(약 1분 40초). RECORD_DEMO=1일 때만 실행한다: e2e/run.sh demo
// 한 페이지(한 영상)에서 진행한다. 자막은 없고, 클릭 지점의 원형 표시는 앱 코드가 아니라 addInitScript로 넣는다.
const path = require('path');
const fs = require('fs');
const { test, expect } = require('@playwright/test');

test.skip(!process.env.RECORD_DEMO, 'RECORD_DEMO=1일 때만 녹화한다');

const DEMO = path.resolve(__dirname, '../src/main/resources/demo');
const OUT = path.resolve(__dirname, '../artifacts/demo');
const VIDEO_TMP = path.resolve(__dirname, '../test-results/demo-video');
// 선택: 실제 이력서로 녹화할 때 환경변수로만 넘긴다(코드·저장소에 값을 두지 않는다).
const V2_PDF = process.env.DEMO_V2_PDF || path.join(DEMO, 'owner-v2.pdf');
const MASK_TERMS = (process.env.DEMO_MASK_TERMS || '').split(',').map((t) => t.trim()).filter(Boolean);
const BASE = process.env.E2E_BASE_URL || 'http://127.0.0.1:18093';
const SIZE = { width: 1280, height: 800 };

// 속도 조절: 단계별 대기 시간과 타이핑 간격에 곱하는 값. 총 길이를 약 1분 40초로 맞춘다.
const PAUSE_SCALE = Number(process.env.DEMO_PAUSE_SCALE || 0.68);
const TYPE_SCALE = Number(process.env.DEMO_TYPE_SCALE || 0.75);
const pause = (page, sec) => page.waitForTimeout(sec * 1000 * PAUSE_SCALE);

/** 마우스 커서 점과 클릭 지점의 원형 표시. bypassCSP:true라서 인라인 스타일을 넣을 수 있다. */
function installClickMarker() {
  const ensure = () => {
    let dot = document.getElementById('__demo_dot');
    if (!dot) {
      dot = document.createElement('div');
      dot.id = '__demo_dot';
      dot.style.cssText = 'position:fixed;left:0;top:0;width:14px;height:14px;margin:-7px 0 0 -7px;border-radius:50%;'
        + 'background:rgba(20,20,20,.75);border:2px solid #fff;z-index:2147483647;pointer-events:none;';
      document.documentElement.appendChild(dot);
    }
    return dot;
  };
  document.addEventListener('mousemove', (e) => {
    const d = ensure();
    d.style.left = `${e.clientX}px`;
    d.style.top = `${e.clientY}px`;
  }, true);
  document.addEventListener('mousedown', (e) => {
    ensure();
    const ring = document.createElement('div');
    ring.style.cssText = 'position:fixed;width:44px;height:44px;margin:-22px 0 0 -22px;border-radius:50%;'
      + 'border:4px solid #ff4d4f;background:rgba(255,77,79,.25);z-index:2147483646;pointer-events:none;'
      + `left:${e.clientX}px;top:${e.clientY}px;`;
    document.documentElement.appendChild(ring);
    ring.animate([{ transform: 'scale(.4)', opacity: 1 }, { transform: 'scale(1.5)', opacity: 0 }],
      { duration: 700, easing: 'ease-out' }).onfinish = () => ring.remove();
  }, true);
}

test('데모 녹화: 샘플 -> 새 버전 게시 -> 평가자 시점 -> 주인 복귀', async ({ browser, playwright }) => {
  fs.mkdirSync(OUT, { recursive: true });
  fs.rmSync(VIDEO_TMP, { recursive: true, force: true });
  const context = await browser.newContext({
    viewport: SIZE,
    recordVideo: { dir: VIDEO_TMP, size: SIZE },
    bypassCSP: true,
    locale: 'ko-KR',
    baseURL: BASE,
  });
  await context.addInitScript(installClickMarker);
  const page = await context.newPage();
  const api = await playwright.request.newContext({ baseURL: BASE });

  /** 커서를 천천히 옮긴 뒤 누른다. */
  async function hclick(locator, opts = {}) {
    await locator.scrollIntoViewIfNeeded();
    const box = await locator.boundingBox();
    const x = box.x + box.width * (opts.fx ?? 0.5);
    const y = box.y + box.height * (opts.fy ?? 0.5);
    await page.mouse.move(x, y, { steps: 22 });
    await pause(page, 0.35);
    await page.mouse.down();
    await page.mouse.up();
    await pause(page, 0.4);
  }
  async function type(locator, text, delay = 110) {
    await hclick(locator);
    await locator.pressSequentially(text, { delay: delay * TYPE_SCALE });
  }
  async function imageLoaded(locator) {
    await expect.poll(() => locator.evaluate((img) => img.complete && img.naturalWidth > 0)).toBe(true);
  }
  async function slowScroll(dy, sec) {
    const steps = Math.max(1, Math.round(sec * 5));
    for (let i = 0; i < steps; i++) {
      await page.mouse.wheel(0, dy / steps);
      await pause(page, sec / steps);
    }
  }

  // ===== a. 시작 -> 샘플 데이터 -> v1에 남은 샘플 평가 =====
  await page.goto('/');
  await pause(page, 2);
  await type(page.locator('#nickname'), '김하늘의 이력서', 120);
  await pause(page, 0.6);
  await hclick(page.locator('#start-btn'));
  await expect(page.locator('#welcome')).toBeVisible();
  await pause(page, 2);
  await hclick(page.locator('#demo-btn'));
  await expect(page.locator('#resume-list li')).toBeVisible();
  await pause(page, 1.5);
  const ownerIdentity = await page.evaluate(() => ({
    token: localStorage.getItem('anonToken'), nickname: localStorage.getItem('anonNickname'),
  }));
  await hclick(page.locator('#resume-list li button').first());
  await pause(page, 1.5);
  await hclick(page.locator('#version-list li[data-no="1"]').getByRole('button', { name: '평가 보기' }));
  await expect(page.locator('.my-page .my-pin').first()).toBeVisible();
  await imageLoaded(page.locator('.my-page img').first());
  await pause(page, 3);
  await slowScroll(260, 3);
  await hclick(page.locator('.my-pinitem').first());
  await pause(page, 3);
  await hclick(page.locator('.my-pinitem').nth(2));
  await pause(page, 3);
  await slowScroll(-260, 1.5);
  await pause(page, 2);

  // ===== b. 새 버전 올리기: v2 업로드 -> 마스킹 미리보기 -> 단어 추가 -> 게시 =====
  await hclick(page.locator('#new-version-link'));
  await expect(page.locator('#upload-btn')).toBeVisible();
  await pause(page, 1.5);
  await page.locator('#file').setInputFiles(V2_PDF);
  await pause(page, 1.2);
  await hclick(page.locator('#upload-btn'));
  await expect(page.locator('#preview-card')).toBeVisible();
  await imageLoaded(page.locator('#pages img').first());
  await pause(page, 2.5);
  await slowScroll(520, 4);
  await pause(page, 2);
  await slowScroll(-520, 1.5);
  let extra = ['바다테크', '누리소프트', '가나다대학교'];
  if (MASK_TERMS.length) {
    // 이름 후보 버튼을 먼저 눌러 보여 준다. 후보가 가릴 단어 목록에 없으면(맞지 않으면) 그 단어를 지운다.
    const cand = page.locator('#name-candidate button');
    if (await cand.count()) {
      await pause(page, 1.5);
      await hclick(cand.first());
      await imageLoaded(page.locator('#pages img').first());
      await pause(page, 2);
    }
    const chips = await page.locator('#terms .chip > span').allTextContents();
    extra = MASK_TERMS.filter((t) => !chips.includes(t));
    // 후보가 맞지 않아 들어간 단어는 지운다.
    for (const c of chips) {
      if (!MASK_TERMS.includes(c)) {
        await hclick(page.locator('#terms .chip').filter({ has: page.locator('span', { hasText: new RegExp(`^${c.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) }) }).locator('button').first());
        await expect(page.locator('#terms .chip > span', { hasText: c })).toHaveCount(0);
        await pause(page, 1);
      }
    }
  }
  for (const t of extra) {
    await type(page.locator('#term'), t, 110);
    await hclick(page.locator('#term-btn'));
    await expect(page.locator('#terms .chip', { hasText: t })).toBeVisible();
    await imageLoaded(page.locator('#pages img').first());
    await pause(page, 2);
  }
  await slowScroll(520, 3);
  await pause(page, 2.5);
  await slowScroll(-520, 1.5);
  await hclick(page.locator('#confirm'));
  await pause(page, 1);
  await hclick(page.locator('#publish-btn'));
  await expect(page.locator('#publish-msg .msg.ok')).toBeVisible();
  await pause(page, 3);

  // ===== c. 평가자 시점: 토큰을 지우고 새 닉네임으로 시작 =====
  await page.evaluate(() => localStorage.clear());
  await page.goto('/');
  await pause(page, 1.5);
  await type(page.locator('#nickname'), '지나가던 평가자', 110);
  await hclick(page.locator('#start-btn'));
  await expect(page.locator('#welcome')).toBeVisible();
  await pause(page, 1);

  // 화면 밖: 평가자가 다른 샘플 이력서를 먼저 평가해 대기열에 요청자 v2만 남긴다.
  const raterToken = await page.evaluate(() => localStorage.getItem('anonToken'));
  const H = { 'X-Anon-Token': raterToken };
  const quickReview = async (t) => {
    const score = t.targetRole === '백엔드 개발자' ? 3 : 4;
    const res = await api.post(`/api/versions/${t.versionId}/reviews`, {
      headers: H,
      data: { totalScore: score, summary: '전체적으로 읽기 쉽게 정리된 이력서입니다. 성과를 더 보강하면 좋겠습니다.',
        pins: [{ page: 1, x: 0.4, y: 0.15, comment: '이 부분을 조금 더 구체적으로 써 주세요.' }] },
    });
    expect(res.status()).toBe(201);
  };
  for (let guard = 0; guard < 20; guard++) {
    const r = await api.get('/api/review-queue/next', { headers: H });
    if (r.status() === 204) break;
    const t = await r.json();
    if (t.versionNo !== 2) { await quickReview(t); continue; }
    const r2 = await api.get(`/api/review-queue/next?skip=${t.versionId}`, { headers: H });
    const t2 = r2.status() === 204 ? t : await r2.json();
    if (t2.versionId === t.versionId) break; // v2만 남았다
    await quickReview(t2);
  }

  await hclick(page.locator('.btn', { hasText: '평가하기' }));
  await expect(page.locator('#review-area')).toBeVisible();
  await expect(page.locator('#target-version')).toContainText('v2');
  const img = page.locator('#pages img').first();
  await imageLoaded(img);
  await pause(page, 3);
  const clickImg = async (fx, fy) => {
    let b = await img.boundingBox();
    const wantY = b.y + b.height * fy;
    if (wantY < 120 || wantY > SIZE.height - 120) {
      await page.evaluate((dy) => window.scrollBy({ top: dy, behavior: 'smooth' }), wantY - 320);
      await pause(page, 1);
      b = await img.boundingBox();
    }
    await page.mouse.move(b.x + b.width * fx, b.y + b.height * fy, { steps: 22 });
    await pause(page, 0.4);
    await page.mouse.down();
    await page.mouse.up();
    await pause(page, 0.5);
  };
  const comments = page.locator('#pin-list textarea');
  await clickImg(0.5, 0.25);
  await comments.nth(0).pressSequentially('이름 아래 한 줄 소개가 수치 중심이라 좋아졌습니다.', { delay: 55 * TYPE_SCALE });
  await pause(page, 1.5);
  await clickImg(0.55, 0.33);
  await comments.nth(1).pressSequentially('결제 응답 시간 40% 단축처럼 숫자가 있어 설득력이 있습니다.', { delay: 55 * TYPE_SCALE });
  await pause(page, 1.5);
  await type(page.locator('#summary'),
    'v1보다 레이아웃과 성과 서술이 훨씬 좋아졌습니다. 수치가 있는 문장이 인상적입니다.', 45);
  await pause(page, 1);
  await hclick(page.locator('input[name=score][value="5"]'));
  await pause(page, 1.5);
  await hclick(page.locator('#submit-btn'));
  await expect(page.locator('#done-area')).toBeVisible();
  await pause(page, 3);

  // ===== d. 주인 시점으로 복귀: v2의 새 핀과 총평, 버전별 평균 총점 =====
  await page.evaluate((id) => {
    localStorage.setItem('anonToken', id.token);
    localStorage.setItem('anonNickname', id.nickname);
  }, ownerIdentity);
  await page.goto('/my.html');
  await pause(page, 2);
  await hclick(page.locator('#resume-list li button').first());
  await expect(page.locator('#version-list li[data-no="2"]')).toContainText('평균 5.00점');
  await pause(page, 3);
  await hclick(page.locator('#version-list li[data-no="2"]').getByRole('button', { name: '평가 보기' }));
  await expect(page.locator('.my-page .my-pin').first()).toBeVisible();
  await imageLoaded(page.locator('.my-page img').first());
  await pause(page, 3);
  await hclick(page.locator('.my-pinitem').first());
  await pause(page, 3);
  await slowScroll(300, 2);
  await pause(page, 2);
  await slowScroll(-300, 1);
  await hclick(page.locator('#version-list li[data-no="1"]').getByRole('button', { name: '평가 보기' }));
  await expect(page.locator('#detail-title')).toContainText('v1');
  await pause(page, 4);
  await page.locator('#version-list').scrollIntoViewIfNeeded();
  await pause(page, 4);

  const video = page.video();
  await context.close();
  const dest = path.join(OUT, 'demo.webm');
  await video.saveAs(dest);
  console.log(`DEMO_VIDEO ${dest} ${fs.statSync(dest).size}`);
});
