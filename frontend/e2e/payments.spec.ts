import { expect, test } from "@playwright/test";

// STK-012: listado con cobro, anulación desde el listado con devolución mixta, resumen y medios de pago.
test("voids from the list with a mixed refund and shows the payment summary and methods", async ({ page }, info) => {
  if (info.project.name === "mobile") await page.setViewportSize({ width: 320, height: 667 });
  const methods = [
    { method: "CASH", label: "Efectivo", active: true }, { method: "CARD", label: "Tarjeta", active: true },
    { method: "SINPE", label: "SINPE Móvil", active: true }, { method: "TRANSFER", label: "Transferencia", active: false },
  ];
  const sale = {
    sale: { branchName: "Centro", customer: "María Rojas", customerPhone: null, customerEmail: null, total: 7600 },
    movement: { folio: "VTA-0004", date: "2026-11-07T16:14:00Z", actor: "Ana Mora", voidId: null as string | null, voidReason: null as string | null, voidFolio: null as string | null },
    lines: [{ itemId: "cafe", name: "Café molido 250 g", sku: "CAF-250", unit: "unidad", quantity: 2, unitPrice: 3800, amount: 7600 }],
    payments: [{ method: "CASH", amount: 5000, received: 10000, reference: null }, { method: "SINPE", amount: 2600, received: null, reference: "94821733" }],
    refund: null as null | object,
  };
  let refunds: unknown;
  let saved: unknown;
  await page.route("**/api/**", route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === "/api/auth/me") return route.fulfill({ json: {
      id: "review", companyId: "revision", role: "COMPANY_ADMIN", fullName: "Revisión", email: "review@example.invalid",
      mustChangePassword: false, modules: [], specialPermissions: [],
    } });
    if (url.pathname === "/api/sales/branches") return route.fulfill({ json: { legacy: false, branches: [{ id: "centro", name: "Centro", location: "principal" }] } });
    if (url.pathname === "/api/sales/payment-methods" && request.method() === "PUT") {
      saved = request.postDataJSON();
      return route.fulfill({ json: methods.map(method => ({ ...method, active: (saved as Record<string, boolean>)[method.method] })) });
    }
    if (url.pathname === "/api/sales/payment-methods") return route.fulfill({ json: methods });
    if (url.pathname === "/api/sales/payments/summary") return route.fulfill({ json: {
      from: url.searchParams.get("from"), to: url.searchParams.get("to"), sales: 3, refunds: 1,
      methods: [
        { method: "CASH", label: "Efectivo", collected: 13251.75, refunded: 5000, net: 8251.75 },
        { method: "CARD", label: "Tarjeta", collected: 11500, refunded: 0, net: 11500 },
        { method: "SINPE", label: "SINPE Móvil", collected: 2600, refunded: 2600, net: 0 },
        { method: "TRANSFER", label: "Transferencia", collected: 0, refunded: 0, net: 0 },
      ],
      days: [{ day: "2026-11-07", methods: [8251.75, 11500, 0, 0], total: 19751.75 }],
    } });
    if (url.pathname.endsWith("/void")) {
      refunds = request.postDataJSON().refunds;
      sale.movement = { ...sale.movement, voidId: "anu", voidReason: "Cliente pidió cancelar", voidFolio: "ANU-0002" };
      sale.refund = { date: "2026-11-07T17:00:00Z", actor: "Revisión", lines: refunds };
      return route.fulfill({ json: sale });
    }
    if (url.pathname === "/api/sales/VTA-0004") return route.fulfill({ json: sale });
    if (url.pathname === "/api/sales") {
      expect(url.searchParams.has("payment")).toBe(true);
      return route.fulfill({ json: { hasMore: false, entries: [
        { id: "4", folio: "VTA-0004", date: sale.movement.date, branchName: "Centro", customer: "María Rojas", total: 7600, voided: false, payment: "paid" },
        { id: "1", folio: "VTA-0001", date: "2026-11-06T15:25:00Z", branchName: "Centro", customer: null, total: 154.31, voided: true, payment: "none" },
      ] } });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/sales");
  await expect(page.locator(".sales-list").getByText("Sin cobro registrado")).toBeVisible();
  await expect(page.getByRole("link", { name: "Anular venta VTA-0001" })).toHaveCount(0);
  await page.getByRole("link", { name: "Anular venta VTA-0004" }).click();
  await expect(page).toHaveURL(/\/sales\/VTA-0004\?anular=1$/);
  await expect(page.getByText("Devolución del dinero")).toBeVisible();
  await page.getByLabel(/Motivo de la anulación/).fill("Cliente pidió cancelar");
  // Devuelve todo en efectivo: cambia el SINPE sugerido y luego deja un faltante para comprobar el bloqueo.
  await page.getByLabel("Medio").nth(1).selectOption("CASH");
  await page.getByLabel("Monto").nth(1).fill("1000");
  await expect(page.getByRole("button", { name: "Confirmar anulación" })).toBeDisabled();
  await page.getByRole("button", { name: "Agregar otro medio" }).click();
  await expect(page.getByLabel("Monto").nth(2)).toHaveValue("1600");
  await page.getByRole("button", { name: "Confirmar anulación" }).click();
  await expect(page.getByText("Devolución registrada")).toBeVisible();
  expect(refunds).toEqual([
    { method: "CASH", amount: "5000.00", reference: null },
    { method: "CASH", amount: "1000.00", reference: null },
    { method: "CASH", amount: "1600.00", reference: null },
  ]);
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);

  await page.goto("/payments");
  await expect(page.getByRole("heading", { name: "Resumen de cobros" })).toBeVisible();
  await expect(page.getByText("₡19 751,75").first()).toBeVisible();
  await expect(page.getByText("3 ventas cobradas")).toBeVisible();
  await expect(page.getByText("₡27 351,75")).toBeVisible();
  await expect(page.getByRole("note").filter({ hasText: "Efectivo:" })).toHaveCount(0);
  await page.getByLabel("Desde").fill("2026-11-08");
  await page.getByLabel("Hasta").fill("2026-11-07");
  await expect(page.getByRole("alert").filter({ hasText: "no puede ser posterior" })).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);

  await page.goto("/payments/methods");
  await page.getByLabel(/Tarjeta/).uncheck();
  await expect(page.getByRole("status").filter({ hasText: "Tarjeta desactivado." })).toBeVisible();
  expect(saved).toEqual({ CASH: true, CARD: false, SINPE: true, TRANSFER: false });
});
