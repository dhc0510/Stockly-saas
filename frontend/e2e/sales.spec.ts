import { expect, test } from "@playwright/test";

test("restores a branch sale draft and retries with the same confirmation key", async ({ page }, info) => {
  if (info.project.name === "mobile") await page.setViewportSize({ width: 320, height: 667 });
  const article = { id: "clavos", name: "Clavos a granel", sku: "CLV", barcode: "812", unit: "kg", balance: 1.250 };
  const document = {
    sale: { branchName: "Norte", customer: "Ana", total: 154.31 },
    movement: { folio: "VTA-0001", date: "2026-11-08T01:04:00Z", actor: "Revisión", voidId: null, voidReason: null, voidFolio: null },
    lines: [{ itemId: article.id, name: article.name, sku: article.sku, unit: article.unit, quantity: 0.125, unitPrice: 1234.50, amount: 154.31 }],
  };
  const keys: string[] = [];
  let voided = false;
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/me") return route.fulfill({ json: {
      id: "review", companyId: "revision", role: "COMPANY_ADMIN", fullName: "Revisión", email: "review@example.invalid",
      mustChangePassword: false, modules: [], specialPermissions: [],
    } });
    if (path === "/api/sales/branches") return route.fulfill({ json: { legacy: false, branches: [
      { id: "norte", name: "Norte", location: "principal" }, { id: "sur", name: "Sur", location: "sur" },
    ] } });
    if (path === "/api/sales/items") return route.fulfill({ json: [{ article, price: 1234.50 }] });
    if (path === "/api/sales" && route.request().method() === "POST") {
      const body = route.request().postDataJSON();
      expect(body.branchId).toBe("norte");
      expect(body.lines[0].quantity).toBe("0.125");
      keys.push(body.requestKey);
      return keys.length === 1 ? route.abort() : route.fulfill({ json: document });
    }
    if (path.endsWith("/void")) voided = true;
    if (path.startsWith("/api/sales/VTA-0001")) return route.fulfill({ json: voided ? {
      ...document, movement: { ...document.movement, voidId: "void", voidReason: "Error en la venta", voidFolio: "ANU-0001" },
    } : document });
    if (path === "/api/sales") return route.fulfill({ json: { entries: [], hasMore: false } });
    return route.fulfill({ json: [] });
  });

  await page.goto("/sales/new");
  await expect(page.getByRole("searchbox")).toBeDisabled();
  await page.getByLabel("Sucursal").selectOption("norte");
  await page.getByLabel("Cliente opcional").fill("Ana");
  await page.getByRole("searchbox").fill("clavos");
  await page.getByRole("button", { name: /Clavos a granel/ }).click();
  await page.getByLabel(/Cantidad/).fill("0,125");
  await page.reload();
  await expect(page.getByLabel("Cliente opcional")).toHaveValue("Ana");
  await expect(page.getByLabel(/Cantidad/)).toHaveValue("0,125");
  await page.getByRole("button", { name: "Revisar venta" }).click();
  await expect(page.getByRole("heading", { name: "Revisar venta" })).toBeVisible();
  await page.getByRole("button", { name: "Confirmar venta" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Revisa tu conexión" })).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Consultar y reintentar" }).click();
  await expect(page).toHaveURL(/\/sales\/VTA-0001$/);
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
  await expect(page.getByText("Detalle de la venta")).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  await page.getByRole("button", { name: "Anular venta", exact: true }).click();
  await page.getByLabel("Motivo de anulación").fill("Error en la venta");
  await page.getByRole("button", { name: "Confirmar anulación" }).click();
  await expect(page.getByText("Anulación ANU-0001")).toBeVisible();
});
