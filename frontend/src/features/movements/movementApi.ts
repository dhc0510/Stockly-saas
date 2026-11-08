import { request } from "../../lib/http";

export type MovementType = "OUT" | "ADJUST";
export type Article = { id: string; name: string; sku: string; barcode: string | null; unit: string; balance: number };
export type Line = { itemId: string; name: string; sku: string; unit: string; quantity: number; balanceAfter: number };
export type Movement = {
  id: string; kind: MovementType | "VOID"; folio: string; actor: string; reason: string; reference: string | null;
  date: string; location: string; voidId: string | null; voidFolio: string | null; voidReason: string | null;
  originalId: string | null; originalFolio: string | null;
};
export type MovementDocument = { movement: Movement; lines: Line[] };
type Common = { requestKey: string; branchId?: string; reason: string; detail: string; reference: string };
export type OutflowInput = Common & { lines: { itemId: string; quantity: number }[] };
export type AdjustmentInput = Common & { lines: { itemId: string; countedQuantity: number; expectedBalance: number }[] };

const base = "/api/inventory/movements";
export const movementApi = {
  list: (search: string, type: string, status: string, page: number, signal: AbortSignal) =>
    request<{ entries: Movement[]; hasMore: boolean }>(base + "?" + new URLSearchParams({ search, type, status, page: String(page) }), { signal }),
  items: (search: string, barcode: boolean, signal?: AbortSignal, branchId?: string) =>
    request<Article[]>(base + "/items?" + new URLSearchParams({ search, barcode: String(barcode), ...(branchId ? { branchId } : {}) }), { signal }),
  get: (id: string, signal?: AbortSignal) => request<MovementDocument>(base + "/" + encodeURIComponent(id), { signal }),
  outflow: (input: OutflowInput) => request<MovementDocument>(base + "/outflows", { method: "POST", body: JSON.stringify(input) }),
  adjustment: (input: AdjustmentInput) => request<MovementDocument>(base + "/adjustments", { method: "POST", body: JSON.stringify(input) }),
  reverse: (id: string, reason: string) =>
    request<MovementDocument>(base + "/" + encodeURIComponent(id) + "/void", { method: "POST", body: JSON.stringify({ reason }) }),
};

export const typeLabels: Record<MovementType, string> = { OUT: "Salida", ADJUST: "Ajuste" };
export const round = (value: number) => Math.round(value * 1000) / 1000;
