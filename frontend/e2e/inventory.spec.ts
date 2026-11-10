import { expect, test, type Page } from "@playwright/test";

const centro = { id: "centro", name: "Centro", code: "C", active: true };
const norte = { id: "norte", name: "Norte", code: "N", active: true };
const base = {
  productId: "cable", productName: "Cable eléctrico", category: "Electricidad", itemId: "red", variantName: "Rojo",
  sku: "CAB-ROJO", barcode: "10001", unit: "metro", branchId: centro.id, branchName: centro.name,
  onHand: 4.875, reserved: 0, available: 4.875, minimumQuantity: 5, lowThreshold: 2, recoveryMargin: 1,
  exhaustedBasis: "AVAILABLE", state: "MINIMUM", targetQuantity: null, restockPaused: false,
};
const rows = [
  base,
  { ...base, branchId: norte.id, branchName: norte.name, onHand: 3, available: 3 },
  { ...base, itemId: "blue", variantName: "Azul", sku: "CAB-AZUL", barcode: "10002", onHand: 0, available: 0, state: "EXHAUSTED" },
  { ...base, productId: "screw", productName: "Tornillo", category: "Ferretería", itemId: "screw", variantName: null,
    sku: "TOR", barcode: null, unit: "unidad", onHand: 12, available: 12, state: "AVAILABLE" },
];
const alert = { ...base, id: "alert", type: "MINIMUM", lastNotifiedAt: "2026-11-10T15:00:00Z" };

async function fixture(page: Page) {
  const state = { dismissed: false, failure: "none" };
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/me") return route.fulfill({ json: {
      id: "admin", companyId: "revision", role: "COMPANY_ADMIN", fullName: "Revisión",
      mustChangePassword: false, modules: [], specialPermissions: [],
    } });
    if (path === "/api/inventory/branches") return route.fulfill({ json: [centro, norte] });
    if (path === "/api/inventory/location-setup") return route.fulfill({ json: { branches: [], pending: false } });
    if (path === "/api/inventory/items") {
      if (state.failure === "offline") return route.abort("internetdisconnected");
      if (state.failure === "server") return route.fulfill({ status: 503, json: { message: "No se pudo leer el saldo." } });
      return route.fulfill({ json: rows });
    }
    if (path === "/api/inventory/alerts") return route.fulfill({ json: state.dismissed ? [] : [alert] });
    if (path === "/api/inventory/alerts/alert/dismiss") {
      state.dismissed = true;
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto("/inventory");
  return state;
}

test("shows branch totals, filters stock and focuses the exact variant from the bell", async ({ page }) => {
  await fixture(page);
  const search = page.getByRole("searchbox");
  const product = page.locator(".inventory-product-card").filter({ has: page.getByRole("heading", { name: "Cable eléctrico", exact: true }) });
  await expect(product.getByText("Disponible total 7,875 metro")).toBeVisible();
  await expect(page.getByText("Disponible total 12 unidad")).toBeVisible();
  await page.getByLabel("Categoría").selectOption("Electricidad");
  await expect(page.getByRole("heading", { name: "Tornillo", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Agotado", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Cable eléctrico · Azul" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Cable eléctrico · Rojo" })).toHaveCount(0);
  await page.getByRole("button", { name: "Todos", exact: true }).click();
  await search.fill("10001");
  await expect(page.getByRole("heading", { name: "Cable eléctrico · Azul" })).toHaveCount(0);

  const bell = page.getByRole("button", { name: /Alertas importantes/ });
  await bell.click();
  const popover = page.getByRole("region", { name: "Alertas importantes" });
  await expect(popover.getByText(/Disponible 4,875 metro/)).toBeVisible();
  await popover.getByRole("button", { name: /Ver existencias/ }).click();
  await expect(page).toHaveURL(/branchId=centro&productId=cable&sku=CAB-ROJO/);
  const focused = page.locator("[data-focused-variant]");
  await expect(focused).toBeFocused();
  await expect(focused.getByRole("heading", { name: "Cable eléctrico · Rojo" })).toBeVisible();
  await expect(focused.getByText(/Norte · 3 metro/)).toBeVisible();
  expect(await page.locator("html").evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  await bell.click();
  await popover.getByRole("button", { name: "Ocultar" }).click();
  await expect(popover.getByText("No tienes alertas activas.")).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Alertas importantes", exact: true }).click();
  await expect(popover.getByText("No tienes alertas activas.")).toBeVisible();
});

test("distinguishes failed and offline requests from a valid empty search", async ({ page }) => {
  const state = await fixture(page);
  await expect(page.getByRole("heading", { name: "Cable eléctrico", exact: true })).toBeVisible();
  state.failure = "server";
  await page.reload();
  await expect(page.getByRole("alert")).toContainText("No se pudo leer el saldo.");
  await expect(page.getByRole("heading", { name: "No hay productos para mostrar" })).toHaveCount(0);
  state.failure = "offline";
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(page.getByRole("alert")).toContainText("Sin conexión");
  state.failure = "none";
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(page.getByRole("heading", { name: "Cable eléctrico", exact: true })).toBeVisible();
  await page.getByRole("searchbox").fill("Sin coincidencia");
  await expect(page.getByRole("heading", { name: "No hay productos para mostrar" })).toBeVisible();
});


test("lets an authorized catalog editor assign a product without branch administration", async ({ page }) => {
  let itemId: string | undefined;
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/me") return route.fulfill({ json: {
      id: "worker", companyId: "revision", role: "WORKER", fullName: "Bodega", mustChangePassword: false,
      modules: [{ moduleKey: "INVENTORY", accessLevel: "READ" }, { moduleKey: "CATALOG", accessLevel: "OPERATE" }], specialPermissions: [],
    } });
    if (path === "/api/inventory/branches") return route.fulfill({ json: [centro] });
    if (path === "/api/inventory/branches/centro/items") {
      itemId = route.request().postDataJSON().itemId;
      return route.fulfill({ status: 201 });
    }
    if (path === "/api/catalog") return route.fulfill({ json: {
      products: [{ id: "cable", name: "Cable eléctrico", items: [{ id: "red", sku: "CAB-ROJO", active: true }] }], hasMore: false,
    } });
    return route.fulfill({ json: [] });
  });
  await page.goto("/branches");
  await expect(page.getByRole("heading", { name: /Centro/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /Crear sucursal|Editar|Accesos|Desactivar|Borrar/ })).toHaveCount(0);
  await page.getByRole("button", { name: "Productos" }).click();
  const dialog = page.getByRole("dialog", { name: "Productos de la sucursal" });
  await dialog.getByLabel("Producto o variante").selectOption("red");
  await dialog.getByRole("button", { name: "Asignar producto" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Producto asignado a la sucursal." })).toBeVisible();
  expect(itemId).toBe("red");
});
