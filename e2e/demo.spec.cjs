// 데모 녹화 스크립트(2분 이내). 영상 구조: 랜딩 문제 문구 -> (편집) v2 결과 콜드 오픈(추이 카드 4초+핀 오버레이 4초+) -> 업로드·마스킹·게시 -> 평가자 -> v2 결과로 끝.
// 콜드 오픈용 구간 시각은 artifacts/demo/segments.env에 기록하고, e2e/to-mp4.sh가 이를 읽어 편집한다.
// RECORD_DEMO=1일 때만 실행한다: e2e/run.sh demo
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
const REAL_PDF = Boolean(process.env.DEMO_V2_PDF);
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
  const t0 = Date.now();
  const since = () => ((Date.now() - t0) / 1000).toFixed(2);
  const seg = {};

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

  // ===== a. 시작: 랜딩의 문제 문구(6~8초) -> 샘플 데이터 -> v1 평가 결과 =====
  await page.goto('/');
  await expect(page.locator('.problem')).toBeVisible();
  await pause(page, 10);
  seg.PROBLEM_END = since();
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

  // ===== b. 새 버전 올리기: v2 업로드 -> 마스킹 미리보기 -> 단어 추가 -> 게시 =====
  seg.BODY_START = since(); // 편집 시 v1 확인 장면은 건너뛰고 여기부터 이어 붙인다
  await hclick(page.locator('#trend-upload, #new-version-link').locator('visible=true').first());
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
  if (REAL_PDF) {
    // 실제 이력서: 이름이 영상에 남지 않도록 이름 후보 버튼을 항상 눌러 첫 후보를 가릴 단어에 넣는다.
    // DEMO_MASK_TERMS는 그 위에 더하는 단어다.
    const cand = page.locator('#name-candidate button');
    await expect(cand.first(), '이름 후보 버튼이 없으면 이름이 가려지지 않는다').toBeVisible();
    await pause(page, 1.5);
    await hclick(cand.first());
    await imageLoaded(page.locator('#pages img').first());
    await pause(page, 2);
    const chips = await page.locator('#terms .chip > span').allTextContents();
    extra = MASK_TERMS.filter((t) => !chips.includes(t));
  }
  for (const t of extra) {
    await type(page.locator('#term'), t, 110);
    await hclick(page.locator('#term-btn'));
    await expect(page.locator('#terms .chip', { hasText: t })).toBeVisible();
    await imageLoaded(page.locator('#pages img').first());
    await pause(page, 2);
  }
  // 마지막 쪽 끝까지 내려야 확인 체크를 켤 수 있다(화면 규칙).
  await page.locator('#pages-end').scrollIntoViewIfNeeded();
  await pause(page, 2.5);
  // 게시 전: 가릴 단어 칩이 1개 이상이어야 한다(실제 이력서에서 이름 누락 방지).
  expect(await page.locator('#terms .chip').count()).toBeGreaterThanOrEqual(1);
  await expect(page.locator('#confirm')).toBeEnabled();
  await hclick(page.locator('#confirm'));
  await pause(page, 1);
  await hclick(page.locator('#publish-btn'));
  await expect(page.locator('#done-card')).toBeVisible();
  await pause(page, 3);

  // ===== c. 평가자 시점: 토큰을 지우고 새 닉네임으로 시작 =====
  await page.evaluate(() => localStorage.clear());
  await page.goto('/');
  await pause(page, 1.5);

  // 화면 밖: 평가자 토큰을 받아 두고(화면은 첫 진입 때 스스로 받지만 대기열을 미리 줄이려면 필요하다),
  // 샘플 이력서를 평가해 대기열에 요청자 v2와 샘플 하나만 남긴다.
  const raterUser = await api.post('/api/users', { data: { nickname: `익명${1000 + Math.floor(Math.random() * 9000)}` } });
  expect(raterUser.status()).toBe(201);
  const raterToken = (await raterUser.json()).token;
  await page.evaluate((t) => localStorage.setItem('anonToken', t), raterToken);
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
  // 샘플 하나(keep)와 요청자 v2만 남기고 나머지를 평가한다. 건너뛰기는 한 번에 하나만 제외하므로 둘을 번갈아 제외해 본다.
  const peek = async (skipId) => {
    const r = await api.get(`/api/review-queue/next${skipId ? `?skip=${skipId}` : ''}`, { headers: H });
    return r.status() === 204 ? null : r.json();
  };
  let keep = null; let v2Id = null;
  for (let guard = 0; guard < 30; guard++) {
    let t = await peek(null);
    if (!t) break;
    if (t.versionNo === 2) v2Id = t.versionId; else if (!keep) keep = t.versionId;
    if (t.versionId === keep || t.versionId === v2Id) {
      const other = await peek(t.versionId);
      if (!other) break;
      if (other.versionNo === 2) v2Id = other.versionId; else if (!keep) keep = other.versionId;
      if (other.versionId === keep || other.versionId === v2Id) {
        // 두 건만 보이는지 확인: 어느 쪽을 제외해도 keep/v2만 나오면 끝
        const third = keep ? await peek(keep) : null;
        if (!third || third.versionId === v2Id || third.versionId === keep) {
          const third2 = v2Id ? await peek(v2Id) : null;
          if (!third2 || third2.versionId === keep || third2.versionId === v2Id) break;
          t = third2;
        } else { t = third; }
      } else { t = other; }
    }
    await quickReview(t);
  }

  await page.goto('/');
  await pause(page, 1.5);
  await hclick(page.locator('main .big-btn', { hasText: '평가하기' }));
  await expect(page.locator('#review-area')).toBeVisible();
  // 첫 이력서가 남겨 둔 샘플이면 건너뛰어 요청자 v2로 넘어간다(후보가 둘이면 반드시 다른 쪽이 나온다).
  for (let i = 0; i < 3 && !(await page.locator('#target-version').textContent()).includes('v2'); i++) {
    await pause(page, 2);
    page.once('dialog', (d) => d.accept());
    await hclick(page.locator('#other-btn'));
    await expect(page.locator('#review-area')).toBeVisible();
    await pause(page, 1);
  }
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
  await expect(page.locator('#done-area .banner')).toBeVisible();
  await expect(page.locator('#review-area:not(.hidden), #empty-area:not(.hidden)')).toBeVisible(); // 다음 이력서(또는 빈 화면)가 자동으로 뜬다
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
  seg.RESULT_START = since(); // 콜드 오픈에 쓸 v2 결과 구간의 시작
  // 콜드 오픈 결과 구간: 추이 카드가 화면 안에 보인 채 4초 이상, 이어서 v2 핀 오버레이가 화면 안에 보인 채 4초 이상(실시간 대기, 배속 무관).
  const inView = async (loc, h) => {
    const b = await loc.boundingBox();
    expect(b, '요소 위치를 읽을 수 없다').not.toBeNull();
    expect(b.y).toBeGreaterThanOrEqual(0);
    expect(b.y + h(b)).toBeLessThanOrEqual(SIZE.height);
  };
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await page.waitForTimeout(1000);
  await expect(page.locator('#trend-card')).toBeVisible();
  await inView(page.locator('#trend-card'), (b) => Math.min(b.height, 200)); // 카드 상단(제목·점수)이 화면 안
  await page.waitForTimeout(4500);
  await inView(page.locator('#trend-card'), (b) => Math.min(b.height, 200));
  await expect(page.locator('#detail-title')).toContainText('v2');
  const pin = page.locator('.my-page .my-pin').first();
  await pin.evaluate((el) => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
  await page.waitForTimeout(1200);
  await inView(pin, (b) => b.height);
  await page.waitForTimeout(4500);
  await inView(pin, (b) => b.height);
  seg.RESULT_END = since();

  const video = page.video();
  await context.close();
  const dest = path.join(OUT, 'demo.webm');
  await video.saveAs(dest);
  fs.writeFileSync(path.join(OUT, 'segments.env'), Object.entries(seg).map(([k, v]) => `${k}=${v}`).join('\n') + '\n');
  console.log(`DEMO_VIDEO ${dest} ${fs.statSync(dest).size}`);
});
