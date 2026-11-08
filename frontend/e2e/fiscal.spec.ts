import { expect, test } from "@playwright/test";

// STK-013: comprobante en la venta (aceptado, rechazado con corrección, nota de crédito), aviso factura/tiquete y Facturación.
test("shows the electronic receipt lifecycle, corrects a rejection and configures billing", async ({ page }, info) => {
  if (info.project.name === "mobile") await page.setViewportSize({ width: 320, height: 700 });
  const doc = (over: object) => ({ id: "fe1", docType: "01", environment: "TEST", consecutive: "00100001010000000012",
    key: "50608112600310112345600100001010000000012148210937", issuedAt: "2026-11-08T15:40:00Z", status: "ACCEPTED", reason: null,
    attempts: 1, sentAt: "2026-11-08T15:40:05Z", resolvedAt: "2026-11-08T15:40:20Z", referenceId: null, replacesId: null,
    mailStatus: "SENT", branchCode: "001", ...over });
  const base = {
    sale: { branchName: "Central", customer: "María Rojas", customerPhone: null, customerEmail: "maria@correo.test", total: 7600,
      customerIdType: "01", customerIdNumber: "109870654" },
    movement: { folio: "VTA-0004", date: "2026-11-08T14:10:00Z", actor: "Ana", voidId: null, voidReason: null, voidFolio: null },
    lines: [{ itemId: "cafe", name: "Café molido 250 g", sku: "CAF-250", unit: "unidad", quantity: 2, unitPrice: 3800, amount: 7600 }],
    payments: [{ method: "CASH", amount: 7600, received: 10000, reference: null }], refund: null,
  };
  let sale: Record<string, unknown> = { ...base, fiscal: [doc({ status: "REJECTED", reason: "La identificación del receptor no está inscrita.", mailStatus: "NONE" })] };
  let corrected: unknown;
  let issuer: unknown;
  await page.route("**/api/**", route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === "/api/auth/me") return route.fulfill({ json: { id: "r", companyId: "c", role: "COMPANY_ADMIN", fullName: "Revisión",
      email: "r@example.invalid", mustChangePassword: false, modules: [], specialPermissions: [] } });
    if (url.pathname === "/api/sales/branches") return route.fulfill({ json: { legacy: false, branches: [{ id: "b", name: "Central", location: "principal" }] } });
    if (url.pathname === "/api/sales/payment-methods") return route.fulfill({ json: [{ method: "CASH", label: "Efectivo", active: true }] });
    if (url.pathname.endsWith("/fiscal/correct")) {
      corrected = request.postDataJSON();
      sale = { ...sale, sale: { ...base.sale, customer: "Comercial Horizonte S.A.", customerIdType: "02", customerIdNumber: "3101654321" },
        fiscal: [...(sale.fiscal as object[]), doc({ id: "fe2", consecutive: "00100001010000000013", status: "PROCESSING", replacesId: "fe1", mailStatus: "NONE" })] };
      return route.fulfill({ json: sale });
    }
    if (url.pathname === "/api/sales/VTA-0004/returns") return route.fulfill({ json: { lines: [], returns: [] } });
    if (url.pathname === "/api/sales/VTA-0004") return route.fulfill({ json: sale });
    if (url.pathname === "/api/sales") return route.fulfill({ json: { hasMore: false, entries: [
      { id: "4", folio: "VTA-0004", date: base.movement.date, branchName: "Central", customer: "María Rojas", total: 7600, voided: false,
        payment: "paid", fiscalType: "01", fiscalStatus: "REJECTED", creditStatus: null },
      { id: "3", folio: "VTA-0003", date: base.movement.date, branchName: "Central", customer: null, total: 6500, voided: true,
        payment: "voided", fiscalType: "04", fiscalStatus: "ACCEPTED", creditStatus: "PROCESSING" },
    ] } });
    if (url.pathname === "/api/billing/settings/issuer") {
      issuer = request.postDataJSON();
      return route.fulfill({ status: 400, json: { code: "BILLING_INVALID", message: "Revisa los datos del emisor.",
        fieldErrors: { activityCode: "La actividad económica tiene 6 dígitos." } } });
    }
    if (url.pathname === "/api/billing/settings") return route.fulfill({ json: {
      environment: "TEST", billingEnabled: true, hasCredentials: true, keySubject: "CN=EMPRESA DE PRUEBA", keyExpiresAt: "2028-03-14T00:00:00Z",
      credentialsCheckedAt: "2026-11-08T10:00:00Z", issuer: { name: "Ferretería de Prueba S.A.", idType: "02", idNumber: "3101123456" },
      activityCode: null, province: null, canton: null, district: null, otherSigns: null, email: null, productionSince: null,
      branches: [{ id: "b", name: "Central", fiscalCode: "001", active: true, sequences: [{ docType: "01", lastNumber: 12 }] }],
      requirements: { serverReady: true, credentials: true, issuer: false, testAccepted: true, productionReady: false } } });
    return route.fulfill({ json: [] });
  });

  await page.goto("/sales");
  await expect(page.locator(".sales-list").getByText("Factura · Rechazado")).toBeVisible();
  await expect(page.locator(".sales-list").getByText("Nota de crédito · Enviado, en revisión")).toBeVisible();

  await page.goto("/sales/VTA-0004");
  await expect(page.getByText("Motivo de Hacienda:")).toBeVisible();
  await page.getByLabel("Tipo de identificación").selectOption("02");
  await page.getByLabel("Número", { exact: true }).fill("123");
  await page.getByRole("button", { name: "Emitir de nuevo" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "10 dígitos" })).toBeVisible();
  await page.getByLabel("Número", { exact: true }).fill("3-101-654321");
  await page.getByLabel("Nombre", { exact: true }).fill("Comercial Horizonte S.A.");
  await page.getByRole("button", { name: "Emitir de nuevo" }).click();
  await expect(page.getByText("Comprobante nuevo enviado a Hacienda")).toBeVisible();
  expect(corrected).toEqual({ idType: "02", idNumber: "3101654321", name: "Comercial Horizonte S.A." });
  await expect(page.getByText("Comprobantes anteriores (1)")).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);

  await page.goto("/sales/new");
  await expect(page.getByText("Se emitirá un tiquete electrónico.")).toBeVisible();
  await page.getByLabel("Tipo de identificación opcional").selectOption("01");
  await expect(page.getByText("Se emitirá una factura electrónica.")).toBeVisible();

  await page.goto("/billing");
  await expect(page.getByRole("heading", { name: "Facturación electrónica" })).toBeVisible();
  await expect(page.getByText("CN=EMPRESA DE PRUEBA")).toBeVisible();
  await expect(page.getByRole("button", { name: "Activar producción" })).toBeDisabled();
  await page.getByLabel(/Actividad económica/).fill("123");
  await page.getByRole("button", { name: "Guardar", exact: true }).first().click();
  await expect(page.getByText("La actividad económica tiene 6 dígitos.").first()).toBeVisible();
  expect(issuer).toMatchObject({ activityCode: "123" });
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});
