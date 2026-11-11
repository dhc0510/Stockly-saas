// STK-018 · Administración del SaaS y suscripciones: pantallas a 375 y 1440 px con la API simulada dentro de Playwright.
// Las reglas del servidor (estados por fecha, límites, pagos, facturas, avisos, permisos) se verifican con el recorrido real
// documentado en docs/04-Testing/STK-018.
import { test, expect, type Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const today = new Date(Date.now() - 6 * 3600e3).toISOString().slice(0, 10);
const plan = { id: "plan-pro", name: "Pro", monthlyPrice: 11300, annualPrice: 113000, description: "Todo Stockly sin límites", active: true, companies: 1, hasPayments: true, version: 0 };
const archived = { ...plan, id: "plan-old", name: "Antiguo", active: false, hasPayments: false, companies: 0 };
const settings = { trialDays: 14, graceDays: 7, noticeDays: 7, trialProducts: 50, trialUsers: 2, trialBranches: 1, paymentInstructions: "SINPE Móvil 8888-0000 a nombre de Stockly S.A.", version: 3 };
const company = {
  id: "company-1", commercialName: "Ferretería El Sol", legalName: "El Sol S.A.", identificationType: "LEGAL", identification: "3101765431",
  contactEmail: "contacto@example.com", phone: "+506 2222-3344", address: "San José", status: "ACTIVE", createdAt: "2026-11-01T16:00:00Z", version: 0,
  administrator: { id: "admin-1", fullName: "Andrea Solís", username: "andrea", email: "andrea@example.com" },
};

function subscription(state: string, payments: unknown[] = []) {
  return {
    companyId: company.id, companyName: company.commercialName, legalName: company.legalName, state,
    planId: payments.length ? plan.id : null, planName: payments.length ? plan.name : null, cycle: payments.length ? "MONTHLY" : null,
    price: payments.length ? plan.monthlyPrice : null, trialEndsOn: "2026-11-15", paidUntil: payments.length ? "2026-12-11" : null,
    expiresOn: payments.length ? "2026-12-11" : "2026-11-15", suspendsOn: payments.length ? "2026-12-18" : "2026-11-22",
    limited: !payments.length, cancelledAt: null, cancelReason: null, createdAt: "2026-11-01T16:00:00Z", payments, noticesSent: [], version: 1,
  };
}

async function mockSaas(page: Page) {
  const state = { payments: [] as Record<string, unknown>[], posted: [] as Record<string, unknown>[] };
  await page.route("**/api/**", (route) => {
    const req = route.request(); const path = new URL(req.url()).pathname; const method = req.method();
    if (path === "/api/auth/csrf") return route.fulfill({ json: { csrfToken: "t", csrfHeaderName: "X-CSRF-TOKEN" } });
    if (path === "/api/auth/me") return route.fulfill({ json: { id: "saas", companyId: null, role: "SAAS_ADMIN", fullName: "Administración Stockly", email: "saas@example.invalid", mustChangePassword: false, modules: [], specialPermissions: [] } });
    if (path === "/api/saas/panel") return route.fulfill({ json: { panel: { states: { TRIAL: 3, ACTIVE: 5, GRACE: 1, SUSPENDED: 1, CANCELLED: 0 }, monthTotal: 56500, monthPayments: 5, monthVoided: 0,
      attention: [{ companyId: company.id, companyName: company.commercialName, state: "GRACE", planName: "Pro", date: "2026-11-18", reason: "Vencida en gracia: se suspende el 18/11/2026" }] },
      invoices: { accepted: 5, problems: 0, pending: 0 } } });
    if (path === "/api/saas/plans" && method === "GET") return route.fulfill({ json: [plan, archived] });
    if (path === "/api/saas/settings") return route.fulfill({ json: settings });
    if (path === "/api/saas/billing") return route.fulfill({ json: { legalName: "Stockly S.A.", idType: "02", idNumber: "3101000000", email: "facturas@stockly.test", activityCode: "620100",
      cabysCode: "8314100000000", province: "1", canton: "01", district: "01", otherSigns: "Oficina central", environment: "TEST", hasCredentials: true, keySubject: "CN=Stockly",
      keyExpiresAt: "2027-11-01T00:00:00Z", credentialsCheckedAt: "2026-11-10T16:00:00Z", serverReady: true, issuerComplete: true, productionReady: false, version: 2 } });
    if (path === "/api/saas/subscriptions") return route.fulfill({ json: [{ companyId: company.id, state: state.payments.length ? "ACTIVE" : "TRIAL", planName: state.payments.length ? "Pro" : null,
      cycle: null, expiresOn: "2026-11-15", suspendsOn: "2026-11-22", lastPayment: null, limited: !state.payments.length }] });
    if (path === "/api/companies" && method === "GET") return route.fulfill({ json: { items: [company], total: 1, page: 0, size: 20 } });
    if (path === `/api/companies/${company.id}`) return route.fulfill({ json: company });
    if (path === `/api/saas/companies/${company.id}/subscription`) return route.fulfill({ json: { subscription: subscription(state.payments.length ? "ACTIVE" : "TRIAL", state.payments), documents: [] } });
    if (path === `/api/saas/companies/${company.id}/payments` && method === "POST") {
      const input = req.postDataJSON(); state.posted.push(input);
      state.payments.unshift({ id: "pay-1", planName: "Pro", cycle: input.cycle, periods: input.periods, amount: input.amount, paidOn: input.paidOn, method: input.method,
        reference: input.reference, coversFrom: today, coversUntil: "2026-12-11", createdBy: "Administración Stockly", createdAt: new Date().toISOString(), voidedAt: null, voidedBy: null, voidReason: null });
      return route.fulfill({ json: { subscription: subscription("ACTIVE", state.payments), documents: [] } });
    }
    return route.fulfill({ status: 404, json: { code: "NOT_FOUND", message: "Sin datos." } });
  });
  return state;
}

async function mockCompanyAdmin(page: Page, mineState: "TRIAL" | "SUSPENDED") {
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/csrf") return route.fulfill({ json: { csrfToken: "t", csrfHeaderName: "X-CSRF-TOKEN" } });
    if (path === "/api/auth/me") return mineState === "SUSPENDED"
      ? route.fulfill({ status: 401, json: { code: "UNAUTHENTICATED", message: "Inicia sesión para continuar." } })
      : route.fulfill({ json: { id: "admin-1", companyId: company.id, role: "COMPANY_ADMIN", fullName: "Andrea Solís", email: "andrea@example.com", mustChangePassword: false, modules: [], specialPermissions: [] } });
    if (path === "/api/auth/login") return route.fulfill({ status: 403, json: { code: "SUBSCRIPTION_SUSPENDED", requestId: "r",
      message: "La suscripción de Ferretería El Sol está suspendida desde el 09/11/2026 por falta de pago. Tus datos están guardados y no se borran.",
      fieldErrors: { role: "ADMIN", companyName: company.commercialName, since: "2026-11-09", instructions: settings.paymentInstructions } } });
    if (path === "/api/subscription") return route.fulfill({ json: { subscription: { state: "TRIAL", planName: null, cycle: null, price: null, trialEndsOn: "2026-11-20", paidUntil: null,
      expiresOn: "2026-11-20", suspendsOn: "2026-11-27", limited: true, usage: { products: 12, users: 2, branches: 1 }, limits: { products: 50, users: 2, branches: 1 },
      paymentInstructions: settings.paymentInstructions, plans: [plan], payments: [], noticeDays: 7 }, documents: [] } });
    return route.fulfill({ status: 404, json: { code: "NOT_FOUND", message: "Sin datos." } });
  });
}

async function capture(page: Page, name: string, project: string) {
  const dir = resolve("../docs/04-Testing/STK-018/screenshots");
  await mkdir(dir, { recursive: true });
  await page.screenshot({ path: resolve(dir, `e2e-${name}-${project}.png`), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}

test("administrador de Stockly: panel, planes, configuración y registro de pago", async ({ page }, testInfo) => {
  const p = testInfo.project.name;
  const state = await mockSaas(page);
  await page.goto("/saas");
  await expect(page.getByRole("heading", { name: "Panel", exact: true })).toBeVisible();
  await expect(page.getByText("Empresas activas")).toBeVisible();
  await expect(page.getByText("Vencida en gracia: se suspende el 18/11/2026")).toBeVisible();
  await capture(page, "panel", p);

  await page.goto("/saas/plans");
  await expect(page.getByRole("heading", { name: "Planes", exact: true })).toBeVisible();
  await expect(page.getByText("Pro", { exact: true }).locator("visible=true").first()).toBeVisible();
  await capture(page, "planes", p);

  await page.goto("/saas/settings");
  await expect(page.getByRole("heading", { name: "Configuración de suscripciones" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Facturación electrónica de Stockly" })).toBeVisible();
  await expect(page.locator("body")).not.toContainText("password");
  await capture(page, "configuracion", p);

  await page.goto(`/companies/${company.id}`);
  const section = page.locator("section", { has: page.locator("#subscription-title") });
  await expect(section.getByRole("heading", { name: /Suscripción/ })).toBeVisible();
  await expect(section.getByText("Todavía no hay pagos registrados.")).toBeVisible();
  await section.getByRole("button", { name: "Registrar pago" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByLabel("Plan")).toHaveValue(plan.id);
  await expect(dialog.getByRole("option", { name: /Antiguo/ })).toHaveCount(0); // archivado: no se ofrece para empezar
  await dialog.getByLabel("Períodos").fill("2");
  await expect(dialog.getByLabel("Monto recibido (₡)")).toHaveValue("22600");
  await dialog.getByLabel("Referencia").fill("SINPE-123");
  await capture(page, "registrar-pago", p);
  await dialog.getByRole("button", { name: "Registrar pago" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Pago registrado" })).toBeVisible();
  expect(state.posted).toHaveLength(1);
  expect(state.posted[0]).toMatchObject({ planId: plan.id, cycle: "MONTHLY", periods: 2, amount: 22600, method: "SINPE", reference: "SINPE-123", paidOn: today });
  await expect(section.getByText(/SINPE/).locator("visible=true").first()).toBeVisible();
  await capture(page, "pago-registrado", p);
});

test("administrador de la empresa: franja de prueba y «Mi suscripción»", async ({ page }, testInfo) => {
  const p = testInfo.project.name;
  await mockCompanyAdmin(page, "TRIAL");
  await page.goto("/subscription");
  await expect(page.getByRole("heading", { name: "Mi suscripción" })).toBeVisible();
  await expect(page.locator(".saas-banner.trial")).toContainText("Productos 12/50");
  await expect(page.getByText(settings.paymentInstructions).first()).toBeVisible();
  await capture(page, "mi-suscripcion", p);
});

test("empresa suspendida: el administrador ve la suspensión y cómo pagar", async ({ page }, testInfo) => {
  const p = testInfo.project.name;
  await mockCompanyAdmin(page, "SUSPENDED");
  await page.goto("/login");
  await page.getByLabel("Usuario o correo").fill("andrea");
  await page.getByLabel("Contraseña").fill("una contraseña cualquiera");
  await page.getByRole("button", { name: "Ingresar" }).click();
  await expect(page.getByRole("heading", { name: "Tu suscripción está suspendida" })).toBeVisible();
  await expect(page.getByText(/Al registrar el pago se reactiva el acceso/)).toBeVisible();
  await capture(page, "suspendida", p);
  await page.getByRole("button", { name: "Volver al inicio de sesión" }).click();
  await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();
});
