import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './demo/stk005-tests', workers: 1, reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:5175' },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', use: { viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true } },
  ],
  webServer: { command: 'npm run dev:stk005-demo', url: 'http://127.0.0.1:5175', reuseExistingServer: true },
})
