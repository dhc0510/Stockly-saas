import { test, expect, type Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import type { Company } from "../src/features/companies/api";

const fixture = (n = 1): Company => ({
  id: `company-${n}`,
  commercialName: [
    "Ferretería El Sol",
    "Papelería Central",
    "Comercial Horizonte",
  ][n - 1],
  legalName: "El Sol S.A.",
  identificationType: "LEGAL",
  identification: `310176543${n}`,
  contactEmail: "contacto@example.com",
  phone: "+506 2222-3344",
  address: "San José",
  status: n === 3 ? "SUSPENDED" : "ACTIVE",
  createdAt: "2026-11-05T16:00:00Z",
  version: 0,
  administrator: {
    id: `admin-${n}`,
    fullName: [
      "Andrés Varela Ruiz",
      "María Fernanda Soto",
      "Carlos Méndez Alfaro",
    ][n - 1],
    username: `admin${n}`,
    email: `admin${n}@example.com`,
  },
});

// Sesión del administrador de Stockly (STK-005). Las demás llamadas (suscripciones de STK-018) responden vacío.
async function mockSaasSession(page: Page) {
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/csrf")
      return route.fulfill({ json: { csrfToken: "t", csrfHeaderName: "X-CSRF-TOKEN" } });
    const subscription = path.match(/^\/api\/saas\/companies\/([^/]+)\/subscription$/);
    if (subscription)
      return route.fulfill({
        json: {
          documents: [],
          subscription: {
            companyId: subscription[1], companyName: "", legalName: "", state: "TRIAL",
            planId: null, planName: null, cycle: null, price: null,
            trialEndsOn: "2026-11-19", paidUntil: null, expiresOn: "2026-11-19", suspendsOn: "2026-11-26",
            limited: true, cancelledAt: null, cancelReason: null, createdAt: "2026-11-05T16:00:00Z",
            payments: [], noticesSent: [], version: 0,
          },
        },
      });
    if (path === "/api/saas/subscriptions") return route.fulfill({ json: [] });
    return path === "/api/auth/me"
      ? route.fulfill({
          json: {
            id: "saas",
            companyId: null,
            role: "SAAS_ADMIN",
            fullName: "Administración Stockly",
            email: "saas@example.invalid",
            mustChangePassword: false,
            modules: [],
            specialPermissions: [],
          },
        })
      : route.fulfill({ status: 404, json: { message: "Sin datos." } });
  });
}

async function mockApi(page: Page) {
  await mockSaasSession(page);
  let companies = [fixture(1), fixture(2), fixture(3)];
  const mutations: string[] = [];
  await page.route("**/api/companies**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const parts = url.pathname.split("/").filter(Boolean);
    const id = parts[2];
    const action = parts[3];
    const body = req.postDataJSON();
    if (req.method() === "GET" && !id) {
      const search = (url.searchParams.get("search") || "").toLowerCase();
      const status = url.searchParams.get("status");
      const items = companies.filter(
        (c) =>
          (!search ||
            `${c.commercialName} ${c.legalName} ${c.identification}`
              .toLowerCase()
              .includes(search)) &&
          (!status || c.status === status),
      );
      await route.fulfill({
        json: { items, total: items.length, page: 0, size: 20 },
      });
      return;
    }
    if (req.method() === "POST" && !id) {
      mutations.push("create");
      const created = {
        ...fixture(1),
        ...body.company,
        id: "created-company",
        administrator: {
          ...body.administrator,
          temporaryPassword: undefined,
          id: "created-admin",
        },
      };
      companies.push(created);
      await route.fulfill({ status: 201, json: created });
      return;
    }
    let company = companies.find((c) => c.id === id);
    if (!company) {
      await route.fulfill({
        status: 404,
        json: { message: "Empresa no encontrada." },
      });
      return;
    }
    if (req.method() === "PATCH") {
      mutations.push("edit");
      company = { ...company, ...body.company, version: company.version + 1 };
      companies = companies.map((c) => (c.id === id ? company! : c));
    }
    if (action === "delete") {
      mutations.push("delete");
      companies = companies.filter((c) => c.id !== id);
      await route.fulfill({ status: 204 });
      return;
    }
    if (action === "suspend" || action === "reactivate") {
      mutations.push(action);
      company!.status = action === "suspend" ? "SUSPENDED" : "ACTIVE";
      company!.version++;
    }
    await route.fulfill({ json: company });
  });
  return mutations;
}

async function capture(page: Page, name: string, project: string) {
  const dir = resolve("../docs/04-Testing/STK-004/screenshots");
  await mkdir(dir, { recursive: true });
  await page.screenshot({
    path: resolve(dir, `${name}-${project}.png`),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
}

test("lista, búsqueda y filtro de estado", async ({ page }, testInfo) => {
  await mockApi(page);
  await page.goto("/companies");
  await expect(
    page.getByText("Ferretería El Sol", { exact: true }),
  ).toBeVisible();
  await capture(page, "01-listado", testInfo.project.name);
  await page.getByRole("searchbox").fill("Papelería");
  await expect(
    page.getByText("Ferretería El Sol", { exact: true }),
  ).not.toBeVisible();
  await expect(
    page.getByText("Papelería Central", { exact: true }),
  ).toBeVisible();
  await page.getByRole("searchbox").fill("");
  if (testInfo.project.name === "mobile")
    await page.getByRole("button", { name: "Filtros", exact: true }).click();
  await page.getByLabel("Estado", { exact: true }).selectOption("SUSPENDED");
  await expect(
    page.getByText("Comercial Horizonte", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Papelería Central", { exact: true }),
  ).not.toBeVisible();
});

test("registro, edición, suspensión, reactivación y borrado confirmado", async ({
  page,
}, testInfo) => {
  const mutations = await mockApi(page);
  await page.goto("/companies/new");
  await capture(page, "02-registro", testInfo.project.name);
  await page
    .getByRole("button", { name: "Registrar empresa", exact: true })
    .click();
  await expect(page.getByLabel("Nombre comercial *")).toBeFocused();
  for (const [label, value] of [
    ["Nombre comercial *", "Variedades Nueva"],
    ["Nombre o razón social *", "Variedades Nueva SA"],
    ["Número de identificación *", "3101999999"],
    ["Correo de contacto *", "contact@example.com"],
    ["Nombre completo *", "Administrador Inicial"],
    ["Nombre de usuario *", "nuevo.admin"],
    ["Correo del administrador *", "nuevo@example.com"],
    ["Contraseña temporal *", "contraseña temporal segura"],
  ])
    await page.getByLabel(label, { exact: true }).fill(value);
  await page.getByLabel("Tipo de identificación *").selectOption("LEGAL");
  await page
    .getByRole("button", { name: "Registrar empresa", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Variedades Nueva", exact: true }),
  ).toBeVisible();
  await capture(page, "03-detalle", testInfo.project.name);
  await page.getByRole("link", { name: "Editar empresa" }).click();
  await capture(page, "04-edicion", testInfo.project.name);
  await page.getByLabel("Nombre comercial *").fill("Variedades Editada");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(
    page.getByRole("heading", { name: "Variedades Editada" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Suspender", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await capture(page, "05-suspender", testInfo.project.name);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  expect(mutations).not.toContain("suspend");
  await expect(
    page.getByRole("button", { name: "Suspender", exact: true }),
  ).toBeFocused();
  await page.getByRole("button", { name: "Suspender", exact: true }).click();
  await page
    .getByRole("button", { name: "Suspender empresa", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Reactivar", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Reactivar", exact: true }).click();
  await capture(page, "06-reactivar", testInfo.project.name);
  await page
    .getByRole("button", { name: "Reactivar empresa", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Eliminar empresa", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Eliminar definitivamente" }),
  ).toBeDisabled();
  await page.getByRole("textbox", { name: /Escribe/ }).fill("incorrecto");
  await expect(
    page.getByRole("button", { name: "Eliminar definitivamente" }),
  ).toBeDisabled();
  await capture(page, "07-eliminar", testInfo.project.name);
  await page
    .getByRole("textbox", { name: /Escribe/ })
    .fill("Variedades Editada");
  await page.getByRole("button", { name: "Eliminar definitivamente" }).click();
  await expect(
    page.getByText("Empresa eliminada definitivamente."),
  ).toBeVisible();
  expect(mutations).toEqual([
    "create",
    "edit",
    "suspend",
    "reactivate",
    "delete",
  ]);
});

test("sin sesión pide ingresar y no consulta empresas", async ({ page }) => {
  const requested: string[] = [];
  await page.route("**/api/**", (route) => {
    requested.push(new URL(route.request().url()).pathname);
    return route.fulfill({
      status: 401,
      json: { code: "UNAUTHENTICATED", message: "Inicia sesión para continuar." },
    });
  });
  await page.goto("/companies");
  await expect(page.getByRole("button", { name: /Ingresar|Iniciar sesión/ })).toBeVisible();
  await expect(page.getByText("Ferretería El Sol")).toHaveCount(0);
  expect(requested.some((p) => p.startsWith("/api/companies"))).toBe(false);
});
