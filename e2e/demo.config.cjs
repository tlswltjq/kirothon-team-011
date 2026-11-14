// 데모 녹화 전용 설정(RECORD_DEMO=1일 때만 실행). 영상은 demo.spec.cjs가 직접 녹화한다.
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: '.',
  testMatch: /demo\.spec\.cjs$/,
  outputDir: '../test-results/demo',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 300000,
  reporter: [['list']],
  use: { baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:18093', locale: 'ko-KR' },
});
