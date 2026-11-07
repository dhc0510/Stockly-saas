import { test, expect } from '@playwright/test';
for (const width of [320, 375, 390, 1440]) {
  test(`STK-008 borrador separado y botones adaptables a ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(() => localStorage.setItem('stockly:receipt:test-company:test-user', JSON.stringify({ reason: 'INITIAL', lines: [], search: 'prueba' })));
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname;
      const json = path === '/api/auth/me' ? { id: 'test-user', companyId: 'test-company', role: 'COMPANY_ADMIN', fullName: 'Prueba', email: 'test@example.com', mustChangePassword: false, modules: [], specialPermissions: [] } : path === '/api/inventory/receipts' ? { entries: [], hasMore: false } : [];
      return route.fulfill({ json });
    });
    await page.goto('/inventory/receipts');
    await expect(page.getByText('Tienes una entrada sin confirmar')).toBeVisible();
    const layout = await page.locator('.receipt-draft-notice').evaluate(notice => {
      const panel = notice.nextElementSibling!;
      return { gap: panel.getBoundingClientRect().top - notice.getBoundingClientRect().bottom, heights: [...notice.querySelectorAll('button')].map(button => button.getBoundingClientRect().height), overflow: document.documentElement.scrollWidth > innerWidth };
    });
    expect(layout.gap).toBeGreaterThanOrEqual(16);
    expect(layout.overflow).toBe(false);
    expect(layout.heights.every(height => height >= 44)).toBe(true);
  });
}