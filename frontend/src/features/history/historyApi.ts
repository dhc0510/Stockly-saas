import { request } from "../../lib/http";

export type MovementKind = "RECEIPT" | "OUT" | "ADJUST" | "VOID";
export type HistoryRow = {
  movementId: string; kind: MovementKind; folio: string; date: string; actor: string; reason: string; reference: string | null;
  voidFolio: string | null; originalFolio: string | null; itemId: string; itemName: string; sku: string; unit: string;
  quantity: number; balanceAfter: number; itemActive: boolean;
};
export type ItemOption = { id: string; name: string; sku: string; barcode: string | null; unit: string; active: boolean };
export type ItemSummary = { item: ItemOption; balance: number };
export type ActorOption = { id: string; name: string };
export type DocumentLine = { itemId: string; name: string; sku: string; unit: string; quantity: number; balanceAfter: number };
export type MovementDocument = {
  movement: {
    id: string; kind: MovementKind; folio: string; actor: string; reason: string; reference: string | null; date: string; location: string;
    voidId: string | null; voidFolio: string | null; voidReason: string | null; originalId: string | null; originalFolio: string | null;
  };
  lines: DocumentLine[];
};
export type HistoryQuery = { search?: string; itemId?: string; type?: string; actorId?: string; from?: string; to?: string; page: number };

const base = "/api/inventory/history";
export const historyApi = {
  list: (query: HistoryQuery, signal: AbortSignal) => {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => { if (value !== undefined && value !== "") params.set(key, String(value)); });
    return request<{ rows: HistoryRow[]; hasMore: boolean }>(base + "?" + params, { signal });
  },
  items: (search: string, signal?: AbortSignal) => request<ItemOption[]>(base + "/items?" + new URLSearchParams({ search }), { signal }),
  item: (id: string, signal?: AbortSignal) => request<ItemSummary>(base + "/items/" + encodeURIComponent(id), { signal }),
  actors: (signal?: AbortSignal) => request<ActorOption[]>(base + "/actors", { signal }),
  movement: (id: string, signal?: AbortSignal) => request<MovementDocument>(base + "/movements/" + encodeURIComponent(id), { signal }),
};

export const kindLabels: Record<MovementKind, string> = { RECEIPT: "Entrada", OUT: "Salida", ADJUST: "Ajuste", VOID: "Anulación" };

const dayFormat = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Costa_Rica" });
/** Día de Costa Rica en formato AAAA-MM-DD. */
export const costaRicaDay = (time: number) => dayFormat.format(new Date(time));
export const shortDay = (day: string) => day.split("-").reverse().join("/");
