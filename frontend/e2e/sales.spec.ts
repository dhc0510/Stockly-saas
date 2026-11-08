import { expect, test } from "@playwright/test";

test("restores a branch sale draft and retries with the same confirmation key", async ({ page }, info) => {
  if (info.project.name === "mobile") await page.setViewportSize({ width: 320, height: 667 });
  const article = { id: "clavos", name: "Clavos a granel", sku: "CLV", barcode: "812", unit: "kg", balance: 1.250 };
  const document = {
    sale: { branchName: "Norte", customer: "Ana", customerPhone: "+506 2222 2222", customerEmail: "ana@example.invalid", total: 154.31 },
    movement: { folio: "VTA-0001", date: "2026-11-08T01:04:00Z", actor: "Revisión", voidId: null, voidReason: null, voidFolio: null },
    lines: [{ itemId: article.id, name: article.name, sku: article.sku, unit: article.unit, quantity: 0.125, unitPrice: 1234.50, amount: 154.31 }],
    payments: [{ method: "CASH", amount: 154.31, received: 200, reference: null }],
    refund: null as null | object,
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
    if (path === "/api/sales/payment-methods") return route.fulfill({ json: [
      { method: "CASH", label: "Efectivo", active: true }, { method: "CARD", label: "Tarjeta", active: true },
      { method: "SINPE", label: "SINPE Móvil", active: true }, { method: "TRANSFER", label: "Transferencia", active: false },
    ] });
    if (path === "/api/sales" && route.request().method() === "POST") {
      const body = route.request().postDataJSON();
      expect(body.branchId).toBe("norte");
      expect(body.lines[0].quantity).toBe("0.125");
      expect(body.customerPhone).toBe(document.sale.customerPhone);
      expect(body.customerEmail).toBe(document.sale.customerEmail);
      expect(body.payments).toEqual([{ method: "CASH", amount: "200.00", reference: null }]);
      keys.push(body.requestKey);
      return keys.length === 1 ? route.abort() : route.fulfill({ json: document });
    }
    if (path.endsWith("/void")) {
      expect(route.request().postDataJSON().refunds).toEqual([{ method: "CASH", amount: "154.31", reference: null }]);
      voided = true;
    }
    if (path.startsWith("/api/sales/VTA-0001")) return route.fulfill({ json: voided ? {
      ...document, movement: { ...document.movement, voidId: "void", voidReason: "Error en la venta", voidFolio: "ANU-0001" },
      refund: { date: "2026-11-08T01:10:00Z", actor: "Revisión", lines: [{ method: "CASH", amount: 154.31, reference: null }] },
    } : document });
    if (path === "/api/sales") return route.fulfill({ json: { entries: [], hasMore: false } });
    return route.fulfill({ json: [] });
  });

  await page.goto("/sales/new");
  await expect(page.getByRole("searchbox")).toBeDisabled();
  await page.getByLabel("Sucursal").selectOption("norte");
  await page.getByLabel("Cliente opcional").fill("Ana");
  await page.getByLabel("Teléfono opcional").fill(document.sale.customerPhone);
  await page.getByLabel("Correo electrónico opcional").fill(document.sale.customerEmail);
  await page.getByRole("searchbox").fill("clavos");
  await page.getByRole("button", { name: /Clavos a granel/ }).click();
  await page.getByLabel(/Cantidad/).fill("0,125");
  await page.reload();
  await expect(page.getByLabel("Cliente opcional")).toHaveValue("Ana");
  await expect(page.getByLabel("Teléfono opcional")).toHaveValue(document.sale.customerPhone);
  await expect(page.getByLabel("Correo electrónico opcional")).toHaveValue(document.sale.customerEmail);
  if (info.project.name === "mobile") {
    await page.getByRole("button", { name: "Abrir menú" }).click();
    await expect(page.locator(".workspace")).toHaveAttribute("inert", "");
    expect((await page.locator(".mobile-drawer-scrim").boundingBox())?.y).toBe(0);
    await page.locator("#primary-navigation").getByRole("button", { name: "Cerrar menú" }).click();
    await expect(page.locator(".workspace")).not.toHaveAttribute("inert", "");
  }
  await expect(page.getByLabel(/Cantidad/)).toHaveValue("0,125");
  await page.getByRole("button", { name: "Revisar y cobrar" }).click();
  await expect(page.getByRole("heading", { name: "Revisar y cobrar" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Confirmar venta y cobro" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Transferencia" })).toHaveCount(0);
  await page.getByRole("button", { name: "Tarjeta" }).click();
  await page.getByLabel("Monto", { exact: true }).fill("200");
  await page.getByRole("button", { name: "Agregar pago" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Solo el efectivo da vuelto" })).toBeVisible();
  await page.getByRole("button", { name: "Efectivo" }).click();
  await page.getByLabel("Monto recibido").fill("200");
  await page.getByRole("button", { name: "Agregar pago" }).click();
  await expect(page.locator(".sales-balance-change")).toContainText("45,69");
  await page.getByRole("button", { name: "Confirmar venta y cobro" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Revisa tu conexión" })).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Consultar y reintentar" }).click();
  await expect(page).toHaveURL(/\/sales\/VTA-0001$/);
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
  await expect(page.getByText("Detalle de la venta")).toBeVisible();
  await expect(page.getByText(document.sale.customerPhone)).toBeVisible();
  await expect(page.getByText(document.sale.customerEmail)).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  await page.getByRole("button", { name: "Anular venta", exact: true }).click();
  await expect(page.getByRole("button", { name: "Confirmar anulación" })).toBeDisabled();
  await page.getByLabel(/Motivo de la anulación/).fill("Error en la venta");
  await expect(page.getByText("Devolución del dinero")).toBeVisible();
  await page.getByRole("button", { name: "Confirmar anulación" }).click();
  await expect(page.getByText("Anulación ANU-0001")).toBeVisible();
  await expect(page.locator("#anulacion").getByText("Motivo", { exact: true })).toBeVisible();
  await expect(page.locator("#anulacion").getByText("Error en la venta")).toBeVisible();
  await expect(page.getByText("Devolución registrada")).toBeVisible();
  await expect(page.getByText("Cobro anulado")).toBeVisible();
});
