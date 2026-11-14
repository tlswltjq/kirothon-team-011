// 핵심 흐름 E2E (tasks.md 작업 11, design.md '테스트 전략'): 컨텍스트 2개(주인, 평가자).
// 주인 v1 업로드·마스킹 미리보기·게시 -> 평가자 핀 2개(수정 1, 삭제 후 재추가 1)·총평·총점 제출
// -> 주인 화면 핀 오버레이·총평 -> 주인 v2 게시 후 v1 핀이 v2에 없음 -> 창 폭 변경 후 핀 위치 비율 유지.
const path = require('path');
const fs = require('fs');
const { test, expect } = require('@playwright/test');

const DEMO = path.resolve(__dirname, '../src/main/resources/demo');
const SHOTS = path.resolve(__dirname, '../artifacts/e2e');
fs.mkdirSync(SHOTS, { recursive: true });

const SUMMARY = '경력 요약을 줄이고 성과에 수치를 넣으면 훨씬 읽기 좋아질 이력서입니다.';
const PIN_A = { x: 0.3, y: 0.2 };
const PIN_B = { x: 0.6, y: 0.45 };
const PIN_C = { x: 0.55, y: 0.7 };

async function shot(page, name) {
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: true });
}

async function start(page, nickname) {
  await page.goto('/');
  await page.locator('#nickname').fill(nickname);
  await page.locator('#start-btn').click();
  await expect(page.locator('#welcome')).toContainText(nickname);
}

async function imageLoaded(locator) {
  await expect.poll(() => locator.evaluate((img) => img.complete && img.naturalWidth > 0)).toBe(true);
}

/** 이미지 위 비율 위치를 눌러 핀을 만든다. */
async function clickAt(img, frac) {
  const box = await img.boundingBox();
  await img.click({ position: { x: box.width * frac.x, y: box.height * frac.y } });
}

/** 핀 중심의 이미지 대비 비율. */
async function pinRatio(pin, img) {
  const [p, i] = [await pin.boundingBox(), await img.boundingBox()];
  return { x: (p.x + p.width / 2 - i.x) / i.width, y: (p.y + p.height / 2 - i.y) / i.height };
}

function near(actual, expected, tol = 0.012) {
  expect(Math.abs(actual - expected), `${actual} vs ${expected}`).toBeLessThan(tol);
}

async function openLatest(page, versionNo) {
  await page.goto('/my.html');
  await page.locator('#resume-list li button').first().click();
  const row = page.locator(`#version-list li[data-no="${versionNo}"]`);
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '평가 보기' }).click();
  await expect(page.locator('#detail-title')).toContainText(`v${versionNo}`);
  return row;
}

test('주인 v1 게시 -> 평가자 핀·총평·총점 -> 핀 오버레이 -> v2에는 핀 없음 -> 창 폭 변경', async ({ browser }) => {
  const owner = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const rater = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();

  // ---- 주인: v1 업로드, 마스킹 미리보기, 게시 ----
  await start(owner, '주인공');
  await owner.goto('/upload.html');
  await owner.locator('#title').fill('백엔드 개발자 이력서');
  await owner.locator('#targetRole').fill('백엔드 개발자');
  await owner.locator('#file').setInputFiles(path.join(DEMO, 'owner-v1.pdf'));
  await owner.locator('#upload-btn').click();
  await expect(owner.locator('#preview-card')).toBeVisible();
  const counts = owner.locator('#summary .counts');
  for (const label of ['이메일', '전화번호', 'URL', '이미지']) {
    await expect(counts).toContainText(new RegExp(`${label} [1-9]\\d*곳`));
  }
  await owner.locator('#term').fill('김하늘');
  await owner.locator('#term-btn').click();
  await expect(owner.locator('#terms .chip')).toContainText('김하늘');
  await expect(owner.locator('#counts, #summary')).toContainText(/지정 단어 [1-9]\d*곳/);
  await expect(owner.locator('#unmatched .msg')).toHaveCount(0);
  await imageLoaded(owner.locator('#pages img').first());
  await shot(owner, '01-owner-v1-masked-preview');
  await owner.locator('#confirm').check();
  await owner.locator('#publish-btn').click();
  await expect(owner.locator('#publish-msg .msg.ok')).toContainText(/v1.{1,2} 게시했습니다/);
  await shot(owner, '02-owner-v1-published');

  // ---- 평가자: 핀 2개(수정 1, 삭제 후 재추가 1), 총평, 총점 ----
  await start(rater, '평가자1');
  await rater.goto('/review.html');
  await expect(rater.locator('#review-area')).toBeVisible();
  const img = rater.locator('#pages img').first();
  await imageLoaded(img);
  const comments = rater.locator('#pin-list textarea');

  await clickAt(img, PIN_A);
  await comments.nth(0).fill('A 처음 코멘트');
  await clickAt(img, PIN_B);
  await comments.nth(1).fill('B 곧 지울 코멘트');
  await expect(rater.locator('.page-wrap .pin')).toHaveCount(2);

  await comments.nth(0).fill('A 수정한 코멘트: 경력 요약을 줄여 보세요');
  await rater.locator('#pin-list .pin-item').nth(1).getByRole('button', { name: '삭제' }).click();
  await expect(rater.locator('.page-wrap .pin')).toHaveCount(1);
  await clickAt(img, PIN_C);
  await comments.nth(1).fill('C 다시 추가한 코멘트: 성과에 수치를 넣어 주세요');
  await expect(rater.locator('.page-wrap .pin')).toHaveCount(2);
  await expect(rater.locator('#pin-count')).toContainText('2 / 50');
  await rater.locator('#summary').fill(SUMMARY);
  await rater.locator('input[name=score][value="4"]').check();
  await shot(rater, '03-rater-pins-before-submit');
  await rater.locator('#submit-btn').click();
  await expect(rater.locator('#done-area')).toBeVisible();

  // ---- 주인: 핀 오버레이와 총평 ----
  const v1Row = await openLatest(owner, 1);
  await expect(v1Row).toContainText('평균 4.00점');
  const pins = owner.locator('.my-page .my-pin');
  await expect(pins).toHaveCount(2);
  await expect(pins.nth(0)).toHaveText('1-1');
  await expect(pins.nth(1)).toHaveText('1-2');
  await expect(owner.locator('#pin-list')).toContainText('A 수정한 코멘트');
  await expect(owner.locator('#pin-list')).toContainText('C 다시 추가한 코멘트');
  await expect(owner.locator('#pin-list')).not.toContainText('곧 지울');
  await expect(owner.locator('#review-list')).toContainText(SUMMARY);
  await expect(owner.locator('#review-list')).toContainText('총점 4점');
  await imageLoaded(owner.locator('.my-page img').first());
  await shot(owner, '04-owner-v1-pin-overlay');

  // ---- 창 폭 변경 후 핀 위치 비율 유지(7.3) ----
  const ownerImg = owner.locator('.my-page img').first();
  for (const width of [1280, 900, 520]) {
    await owner.setViewportSize({ width, height: 1000 });
    await expect(pins).toHaveCount(2);
    const a = await pinRatio(pins.nth(0), ownerImg);
    const c = await pinRatio(pins.nth(1), ownerImg);
    near(a.x, PIN_A.x); near(a.y, PIN_A.y);
    near(c.x, PIN_C.x); near(c.y, PIN_C.y);
  }
  await shot(owner, '05-owner-v1-narrow');
  await owner.setViewportSize({ width: 1280, height: 1000 });

  // ---- 주인: v2 업로드·게시. v1 핀은 v2에 없다(9.8) ----
  await owner.goto('/my.html');
  await owner.locator('#resume-list li button').first().click();
  await owner.locator('#new-version-link').click();
  await owner.locator('#file').setInputFiles(path.join(DEMO, 'owner-v2.pdf'));
  await owner.locator('#upload-btn').click();
  await expect(owner.locator('#preview-card')).toBeVisible();
  await expect(owner.locator('#terms .chip')).toContainText('김하늘'); // 직전 버전 단어 이월
  await owner.locator('#confirm').check();
  await owner.locator('#publish-btn').click();
  await expect(owner.locator('#publish-msg .msg.ok')).toContainText(/v2.{1,2} 게시했습니다/);

  const v2Row = await openLatest(owner, 2);
  await expect(v2Row).toContainText('평가 없음');
  await expect(owner.locator('.my-page .my-pin')).toHaveCount(0);
  await expect(owner.locator('#pin-empty')).toBeVisible();
  await expect(owner.locator('#review-empty')).toBeVisible();
  await imageLoaded(owner.locator('.my-page img').first());
  await shot(owner, '06-owner-v2-no-pins');

  // v1은 그대로 핀 2개
  await owner.locator('#version-list li[data-no="1"]').getByRole('button', { name: '평가 보기' }).click();
  await expect(owner.locator('.my-page .my-pin')).toHaveCount(2);
});
