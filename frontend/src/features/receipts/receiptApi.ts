import { request } from "../../lib/http";

export type Article = { id: string; name: string; sku: string; barcode: string | null; unit: string; balance: number };
export type Line = { itemId: string; name: string; sku: string; unit: string; quantity: number; balanceAfter: number };
export type Movement = {
  id: string; kind: "RECEIPT" | "VOID"; folio: string; actor: string; reason: string; reference: string | null;
  date: string; location: string; voidId: string | null; voidFolio: string | null; voidReason: string | null;
  originalId: string | null; originalFolio: string | null;
};
export type Receipt = { movement: Movement; lines: Line[] };
export type ReceiptInput = { requestKey: string; reason: string; detail: string; reference: string; lines: { itemId: string; quantity: number }[] };

const base = "/api/inventory/receipts";
export const receiptApi = {
  list: (search: string, status: string, page: number, signal: AbortSignal) =>
    request<{ entries: Movement[]; hasMore: boolean }>(base + "?" + new URLSearchParams({ search, status, page: String(page) }), { signal }),
  items: (search: string, barcode: boolean, signal?: AbortSignal) =>
    request<Article[]>(base + "/items?" + new URLSearchParams({ search, barcode: String(barcode) }), { signal }),
  get: (id: string, signal?: AbortSignal) => request<Receipt>(base + "/" + encodeURIComponent(id), { signal }),
  confirm: (input: ReceiptInput) => request<Receipt>(base, { method: "POST", body: JSON.stringify(input) }),
  reverse: (id: string, reason: string) =>
    request<Receipt>(base + "/" + encodeURIComponent(id) + "/void", { method: "POST", body: JSON.stringify({ reason }) }),
};
const number = new Intl.NumberFormat("es-CR", { maximumFractionDigits: 3 });
export const amount = (value: number, unit: string) =>
  number.format(value) + " " + (unit === "unidad" && Math.abs(value) !== 1 ? "unidades" : unit);
const clock = new Intl.DateTimeFormat("es-CR", {
  timeZone: "America/Costa_Rica", day: "numeric", month: "short", year: "numeric",
  hour: "numeric", minute: "2-digit", hour12: true,
});
export function receiptDate(value: string) {
  const parts = Object.fromEntries(clock.formatToParts(new Date(value)).map(part => [part.type, part.value]));
  return parts.day + " " + parts.month.replace(/\.$/, "") + " " + parts.year + ", " + parts.hour + ":" +
    parts.minute + " " + (parts.dayPeriod.startsWith("a") ? "a. m." : "p. m.");
}
export const failureMessage = (failure: unknown) => failure instanceof Error ? failure.message : "No se pudo completar la operación.";
