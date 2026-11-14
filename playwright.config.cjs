// E2E 설정. 앱은 e2e/run.sh가 127.0.0.1의 빈 포트에 임시 DATA_DIR로 띄우고, 이 설정은 E2E_BASE_URL로 접속한다.
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  testMatch: /resume-flow\.spec\.cjs$/,
  outputDir: 'test-results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180000,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:18093',
    viewport: { width: 1280, height: 1000 },
    locale: 'ko-KR',
  },
});
