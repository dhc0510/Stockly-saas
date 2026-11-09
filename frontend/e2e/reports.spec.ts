import { expect, test } from "@playwright/test";

// STK-017: Reportes con período, pestañas (lista en celular), cálculo mostrado, exportación, validación de fechas e impresión.
test("shows reports by period, switches reports, exports and validates dates", async ({ page }, info) => {
  const mobile = info.project.name === "mobile";
  const context = (from = "2026-11-02", to = "2026-11-08") => ({ companyName: "Ferretería de Prueba", branchId: null, branchName: null, from, to,
    previousFrom: "2026-10-26", previousTo: "2026-11-01", grouping: "DAY", branches: [{ id: "b1", name: "Central" }, { id: "b2", name: "Norte" }] });
  const kpis = (gross:number, returns:number, laterVoids:number, sales:number) => ({ gross, returns, laterVoids, net: gross - returns - laterVoids, sales,
    average: sales ? gross / sales : 0, sameDayVoids: 1, sameDayVoidAmount: 2500 });
  const product = { itemId: "cafe", productName: "Café molido", variantName: null, sku: "CAF", category: "Bebidas", unit: "unidad", quantity: 3, amount: 12000, returned: 1000, share: 0.6 };
  const requests:string[] = []; let exported = "";
  await page.route("**/api/**", route => {
    const url = new URL(route.request().url()); requests.push(url.pathname + url.search);
    const ctx = context(url.searchParams.get("from") ?? undefined, url.searchParams.get("to") ?? undefined);
    if (url.pathname === "/api/auth/me") return route.fulfill({ json: { id: "r", companyId: "c", role: "WORKER", fullName: "Revisión", email: "r@example.invalid",
      mustChangePassword: false, modules: [{ moduleKey: "REPORTS", accessLevel: "READ" }], specialPermissions: [] } });
    if (url.pathname === "/api/reports/summary") return route.fulfill({ json: { context: ctx, current: kpis(20000, 1000, 500, 4), previous: kpis(10000, 0, 0, 2),
      series: [{ start: "2026-11-07", sales: 1, gross: 8000, returns: 0, voids: 0, net: 8000 }, { start: "2026-11-08", sales: 3, gross: 12000, returns: 1000, voids: 500, net: 10500 }], top: [product] } });
    if (url.pathname === "/api/reports/products") return route.fulfill({ json: { context: ctx, gross: 20000, returned: 1000,
      categories: [{ category: "Bebidas", products: 1, amount: 12000, share: 0.6 }], products: [product], categoryOptions: ["Bebidas"] } });
    if (url.pathname === "/api/reports/payments") return route.fulfill({ json: { context: ctx, methods: [{ method: "CASH", label: "Efectivo", payments: 4, collected: 22500, refunded: 4000, net: 18500 }],
      total: { method: "TOTAL", label: "Total", payments: 4, collected: 22500, refunded: 4000, net: 18500 }, unpaidSales: 0, unpaidAmount: 0, netSales: 18500 } });
    if (url.pathname === "/api/reports/export") { exported = url.search;
      return route.fulfill({ body: "Indicador\r\n", headers: { "Content-Type": "text/csv", "Content-Disposition": "attachment; filename=\"reportes-todas-2026-11-02-a-2026-11-08-productos.csv\"" } }); }
    return route.fulfill({ json: [] });
  });

  await page.goto("/reports");
  await expect(page.getByRole("heading", { name: "Reportes" })).toBeVisible();
  if (!mobile) await expect(page.getByRole("link", { name: "Reportes" })).toHaveCount(1); // en celular vive en el menú lateral cerrado
  const breakdown = page.locator(".reports-lines").first();
  await expect(breakdown.getByText("Anulaciones de ventas anteriores")).toBeVisible();
  await expect(page.locator(".reports-kpi").first()).toContainText("▲ 85 %");
  if (mobile) { await expect(page.locator(".reports-tabs")).toBeHidden(); await page.getByLabel("Ver reporte").selectOption("products"); }
  else { await expect(page.getByLabel("Ver reporte")).toBeHidden(); await page.getByRole("tab", { name: "Productos" }).click(); }
  await expect(page.locator(".reports-page").getByText("Café molido").filter({ visible: true }).first()).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);

  await page.getByRole("button", { name: "Exportar" }).click();
  const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("menuitem", { name: /CSV/ }).click()]);
  expect(download.suggestedFilename()).toBe("reportes-todas-2026-11-02-a-2026-11-08-productos.csv");
  expect(exported).toContain("format=csv"); expect(exported).toContain("report=products");

  await page.getByRole("button", { name: "Personalizado" }).click();
  await page.getByLabel("Desde").fill("2026-11-08"); await page.getByLabel("Hasta").fill("2026-11-01");
  await expect(page.getByText("La fecha «Desde» debe ser igual o anterior a «Hasta».")).toBeVisible();
  await expect(page.getByRole("button", { name: "Exportar" })).toBeDisabled();
  await page.getByLabel("Hasta").fill("2026-11-08"); await page.getByLabel("Desde").fill("2026-11-01");
  await page.getByRole("button", { name: "Aplicar" }).click();
  await expect.poll(() => requests.some(r => r.includes("from=2026-11-01") && r.includes("to=2026-11-08"))).toBe(true);

  await page.getByRole("button", { name: "Imprimir" }).click();
  await expect(page.locator(".reports-sheet").getByText("Ferretería de Prueba")).toBeVisible();
  await expect(page.locator(".reports-sheet").getByText("Generado por Revisión", { exact: false })).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});
