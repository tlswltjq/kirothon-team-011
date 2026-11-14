// 발표 HTML을 PDF와 페이지별 PNG로 변환한다. 사용: node presentation/render.cjs
const { chromium } = require('@playwright/test');
const path = require('path');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.goto('file://' + path.resolve(__dirname, 'slides.html'));
  await page.waitForTimeout(500);
  await page.pdf({ path: path.resolve(__dirname, '..', 'PRESENTATION.pdf'), width: '1280px', height: '720px', printBackground: true, preferCSSPageSize: true });
  const n = await page.locator('section.slide').count();
  for (let i = 0; i < n; i++) {
    await page.locator('section.slide').nth(i).screenshot({ path: path.resolve(__dirname, '..', 'artifacts', 'slides', `s${i + 1}.png`) });
  }
  console.log('slides', n);
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
