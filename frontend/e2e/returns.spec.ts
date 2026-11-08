import { expect, test } from "@playwright/test";

test("returns a line, restocks only the sellable units and retries with the same key", async ({ page }, info) => {
  if (info.project.name === "mobile") await page.setViewportSize({ width: 320, height: 667 });
  const fiscal = { status: "WAITING", reason: null, consecutive: null };
  const payment = { method: "CARD", amount: 3000, received: null, reference: "Prueba" };
  const line = { itemId: "guantes", name: "Guantes de trabajo", sku: "GUA-1", unit: "unidad", sold: 3, returned: 0, remaining: 3, unitPrice: 1000 };
  const context = { saleId: "sale", saleFolio: "VTA-0003", branch: "Centro", voided: false, lines: [line], payments: [payment], returns: [], fiscal };
  const returned = { itemId: line.itemId, name: line.name, sku: line.sku, unit: line.unit, returned: 2, restocked: 1, amount: 2000 };
  const quote = { amount: 2000, refund: 2000, lines: [returned] };
  const receipt = { folio: "DEV-0001", saleFolio: context.saleFolio, branch: context.branch, date: "2026-11-08T19:00:00Z",
    actor: "Revisión", reason: "Producto dañado", movementId: "mov", amount: 2000, lines: [returned],
    refunds: [{ method: "CARD", amount: 2000, reference: null }], fiscal };
  const keys: string[] = [];
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/me") return route.fulfill({ json: {
      id: "review", companyId: "revision", role: "COMPANY_ADMIN", fullName: "Revisión", email: "review@example.invalid",
      mustChangePassword: false, modules: [], specialPermissions: [],
    } });
    if (path === "/api/sales/payment-methods") return route.fulfill({ json: [{ method: "CARD", label: "Tarjeta", active: true }] });
    if (path.endsWith("/returns/preview")) {
      expect(route.request().postDataJSON().lines).toEqual([{ itemId: line.itemId, returned: "2", restocked: "1" }]);
      return route.fulfill({ json: quote });
    }
    if (path.endsWith("/returns/DEV-0001")) return route.fulfill({ json: receipt });
    if (path.endsWith("/returns")) {
      if (route.request().method() === "GET") return route.fulfill({ json: context });
      const input = route.request().postDataJSON();
      expect(input.expectedRefund).toBe(2000);
      expect(input.refunds).toEqual([{ method: "CARD", amount: "2000.00", reference: null }]);
      keys.push(input.requestKey);
      return keys.length === 1 ? route.abort() : route.fulfill({ json: receipt });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/sales/VTA-0003/returns/new");
  await page.getByLabel("Devuelve Guantes de trabajo").fill("2");
  await page.getByRole("button", { name: "Reponer todo" }).click();
  await expect(page.getByLabel("Repone Guantes de trabajo")).toHaveValue("2");
  await page.getByLabel("Repone Guantes de trabajo").fill("1");
  await page.getByRole("combobox", { name: "Motivo", exact: true }).selectOption("Producto dañado");
  await page.getByRole("button", { name: "Revisar devolución" }).click();
  await expect(page.getByRole("heading", { name: "Revisar devolución" })).toBeVisible();
  await expect(page.getByText("Repone 1 unidad · No apto 1 unidad")).toBeVisible();
  await page.getByRole("button", { name: "Confirmar devolución" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Revisa tu conexión" })).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Consultar y reintentar" }).click();
  await expect(page).toHaveURL(/\/sales\/VTA-0003\/returns\/DEV-0001$/);
  expect(keys[0]).toBe(keys[1]);
  await expect(page.getByText("Esperando comprobante original")).toBeVisible();
  await expect(page.getByRole("link", { name: "Ver reposición en Historial" })).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});
