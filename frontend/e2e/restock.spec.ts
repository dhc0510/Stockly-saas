import { expect, test } from "@playwright/test";

// STK-016: Por reponer con contador, cálculo de «A pedir», configuración con nivel deseado y «No reponer», exportación e impresión.
test("lists items to restock, configures the desired level and pauses an item", async ({ page }, info) => {
  if (info.project.name === "mobile") await page.setViewportSize({ width: 320, height: 700 });
  const item = (over: object) => ({ productId: "p", productName: "Cable eléctrico", category: "Electricidad", itemId: "cable", variantName: null, sku: "CAB",
    unit: "metro", branchId: "b1", branchName: "Central", state: "MINIMUM", available: 40, minimumQuantity: 50, recoveryMargin: 1, targetQuantity: 200,
    toOrder: 160, basis: "TARGET", ...over });
  let paused = false; let saved: Record<string, unknown> | undefined; let exported = "";
  const restock = () => ({ companyName: "Ferretería de Prueba", totals: { exhausted: 1, minimum: 1, low: 0 },
    rows: [item({ itemId: "cafe", productName: "Café molido", sku: "CAF", state: "EXHAUSTED", available: 0, minimumQuantity: null, targetQuantity: null, toOrder: null, basis: "NONE", category: null }),
      ...(paused ? [] : [item({})])],
    paused: paused ? [{ productId: "p", productName: "Cable eléctrico", itemId: "cable", variantName: null, sku: "CAB", unit: "metro", branchId: "b1",
      branchName: "Central", available: 40, pausedBy: "Revisión", pausedAt: "2026-11-08T20:00:00Z" }] : [] });
  await page.route("**/api/**", route => {
    const request = route.request(); const url = new URL(request.url());
    if (url.pathname === "/api/auth/me") return route.fulfill({ json: { id: "r", companyId: "c", role: "COMPANY_ADMIN", fullName: "Revisión",
      email: "r@example.invalid", mustChangePassword: false, modules: [], specialPermissions: [] } });
    if (url.pathname === "/api/inventory/branches") return route.fulfill({ json: [{ id: "b1", name: "Central", code: "C", address: "", phone: "", contactEmail: "", managerName: "", active: true },
      { id: "b2", name: "Norte", code: "N", address: "", phone: "", contactEmail: "", managerName: "", active: true }] });
    if (url.pathname === "/api/inventory/restock/count") return route.fulfill({ json: { count: paused ? 1 : 2 } });
    if (url.pathname === "/api/inventory/restock/export") { exported = url.search;
      return route.fulfill({ body: "Sucursal,Producto\r\n", headers: { "Content-Type": "text/csv", "Content-Disposition": "attachment; filename=\"por-reponer-todas-2026-11-08.csv\"" } }); }
    if (url.pathname === "/api/inventory/restock") return route.fulfill({ json: restock() });
    if (url.pathname === "/api/inventory/items") return route.fulfill({ json: [{ productId: "p", productName: "Cable eléctrico", category: "Electricidad", itemId: "cable",
      variantName: null, sku: "CAB", barcode: null, unit: "metro", branchId: "b1", branchName: "Central", onHand: 40, reserved: 0, available: 40, minimumQuantity: 50,
      lowThreshold: 2, recoveryMargin: 1, exhaustedBasis: "AVAILABLE", state: "MINIMUM", targetQuantity: 200, restockPaused: paused }] });
    if (url.pathname.endsWith("/thresholds")) { saved = request.postDataJSON(); paused = saved?.restockPaused === true; return route.fulfill({ status: 204 }); }
    return route.fulfill({ json: [] });
  });

  await page.goto("/inventory/restock");
  await expect(page.getByRole("heading", { name: "Por reponer" })).toBeVisible();
  const list = page.locator(".restock-list");
  await expect(list.getByText("160 metro").filter({ visible: true }).first()).toBeVisible();
  await expect(list.getByText("Sin dato para sugerir").filter({ visible: true }).first()).toBeVisible();
  if (info.project.name === "mobile") await expect(page.locator(".restock-cards")).toBeVisible();
  else await expect(page.locator(".restock-table")).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);

  await page.getByRole("button", { name: "Exportar" }).click();
  const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("menuitem", { name: /CSV/ }).click()]);
  expect(download.suggestedFilename()).toBe("por-reponer-todas-2026-11-08.csv");
  expect(exported).toContain("format=csv");

  await page.locator(".restock-list").locator("tr, .restock-card").filter({ hasText: "Cable eléctrico", visible: true }).getByRole("button", { name: /Configurar/ }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Reponer hasta (nivel deseado)").fill("30");
  await expect(dialog.getByText("El nivel deseado debe ser mayor que el mínimo (50 metro).")).toBeVisible();
  await dialog.getByLabel("Reponer hasta (nivel deseado)").fill("100");
  await expect(dialog.getByText(/pedir 60 metro/)).toBeVisible();
  await dialog.getByLabel(/No reponer en esta sucursal/).check();
  await dialog.getByRole("button", { name: "Guardar cambios" }).click();
  expect(saved).toMatchObject({ minimumQuantity: 50, targetQuantity: 100, restockPaused: true });
  await expect(page.getByText("No se reponen (1)")).toBeVisible();
  await expect(page.locator(".restock-nav-count")).toHaveText("1");

  await page.getByRole("button", { name: "Imprimir" }).click();
  await expect(page.locator(".restock-sheet").getByText("Ferretería de Prueba")).toBeVisible();
  await expect(page.locator(".restock-sheet").getByText("Generado por Revisión", { exact: false })).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});
