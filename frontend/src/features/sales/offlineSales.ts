import Dexie, { type EntityTable } from "dexie";
import { useEffect, useState } from "react";
import { authApi, ensureCsrf, type SessionUser } from "../auth/api";
import { ApiError, request } from "../../lib/http";
import type { Article } from "../receipts/receiptApi";
import type { MethodState } from "./payments";

const api = "/api/sales/offline";
const eventName = "stockly:offline-sales";
export const ownerKey = (user: SessionUser) => user.companyId + ":" + user.id;
export type OfflineBranch = { id: string; name: string; location: string | null };
export type OfflineItem = { article: Article; price: number; proof: string };
export type Snapshot = {
  id: string; issuedAt: string; expiresAt: string; items: OfflineItem[]; methods: MethodState[];
};
export type SaleInput = {
  requestKey: string; branchId: string; customer: string; customerPhone?: string; customerEmail?: string;
  customerIdType: string | null; customerIdNumber: string | null;
  lines: { itemId: string; quantity: string; expectedPrice: number | null }[];
  payments: { method: string; amount: string | number; reference: string | null }[];
};
type Entry = {
  id: string; reference: string; state: "REVIEW" | "SYNCED" | "CLOSED"; folio: string | null;
  soldAt: string; branchName: string; sale: SaleInput;
  issue: { message: string; fields: Record<string, string> } | null;
  items?: Record<string, string>; resolver: string | null; closeReason: string | null; refundDetail: string | null;
};
export type { Entry };
type CachedBranch = Snapshot & { key: string; owner: string; branch: OfflineBranch };
export type QueuedSale = {
  key: string; owner: string; branch: OfflineBranch; soldAt: string; sale: SaleInput;
  snapshotId: string; proofs: Record<string, string>; items: Record<string, string>; result?: Entry; error?: string; settledAt?: string;
};

const db = new Dexie("stockly-offline-sales") as Dexie & {
  branches: EntityTable<CachedBranch, "key">;
  queue: EntityTable<QueuedSale, "key">;
};
db.version(1).stores({ branches: "key,owner", queue: "key,owner" });
const notify = () => window.dispatchEvent(new Event(eventName));
const preparing = new Map<string, Promise<void>>();
const branchKey = (owner: string, id: string) => owner + ":" + id;
const queuedKey = (owner: string, id: string) => owner + ":" + id;
const localQueues = new Map<string, Promise<void>>();

async function prepare(owner: string) {
  const current = preparing.get(owner);
  if (current) return current;
  const work = (async () => {
    await ensureCsrf();
    if (ownerKey(await authApi.me()) !== owner) return;
    const { branches } = await request<{ branches: OfflineBranch[] }>("/api/sales/branches");
    const previous = await db.branches.where("owner").equals(owner).toArray();
    const authorized = branches.filter(branch => !!branch.location);
    await db.branches.bulkDelete(previous.filter(item => !authorized.some(branch => branch.id === item.branch.id)).map(item => item.key));
    const jobs = authorized.map(async branch => {
      const snapshot = await request<Snapshot>(api + "/snapshot?" + new URLSearchParams({ branchId: branch.id }), { method: "POST" });
      await db.branches.put({ ...snapshot, key: branchKey(owner, branch.id), owner, branch });
    });
    const results = await Promise.allSettled(jobs);
    notify();
    const failed = results.find(result => result.status === "rejected");
    if (failed) throw failed.reason;
  })().finally(() => preparing.delete(owner));
  preparing.set(owner, work);
  return work;
}

export async function pendingSales(owner: string) {
  return (await db.queue.where("owner").equals(owner).toArray()).filter(sale => !sale.result);
}

export function savedOffline(owner: string, id: string) { return db.queue.get(queuedKey(owner, id)); }

function estimated(item: OfflineItem, queue: QueuedSale[], branch: string, issuedAt: string) {
  const allocated = queue.filter(row => row.branch.id === branch && row.result?.state !== "CLOSED" &&
    (row.result?.state !== "SYNCED" || !row.settledAt || Date.parse(row.settledAt) >= Date.parse(issuedAt)))
    .flatMap(row => row.sale.lines).filter(line => line.itemId === item.article.id)
    .reduce((total, line) => total + Math.round(Number(line.quantity) * 1000), 0);
  return Math.max(0, Math.round(item.article.balance * 1000) - allocated) / 1000;
}

export function offlineItems(snapshot: CachedBranch | undefined, queue: QueuedSale[], search: string) {
  if (!snapshot) return [];
  const normalize = (value: string) => value.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
  const term = normalize(search.trim());
  return snapshot.items.filter(item => term && [item.article.name, item.article.sku, item.article.barcode ?? ""]
    .some(value => normalize(value).includes(term))).slice(0, 12).map(item => ({
    ...item, article: { ...item.article, balance: estimated(item, queue, snapshot.branch.id, snapshot.issuedAt) },
  }));
}

export async function saveOffline(owner: string, sale: SaleInput) {
  return db.transaction("rw", db.branches, db.queue, async () => {
    const snapshot = await db.branches.get(branchKey(owner, sale.branchId));
    const now = new Date();
    if (!snapshot || Date.parse(snapshot.expiresAt) <= now.getTime() || Date.parse(snapshot.issuedAt) > now.getTime())
      throw new Error("Reconecta para verificar la sesión y preparar la sucursal.");
    const key = queuedKey(owner, sale.requestKey);
    if (await db.queue.get(key)) return sale.requestKey;
    const queue = await db.queue.where("owner").equals(owner).toArray();
    const proofs: Record<string, string> = {};
    const items: Record<string, string> = {};
    const fields: Record<string, string> = {};
    for (const line of sale.lines) {
      const item = snapshot.items.find(candidate => candidate.article.id === line.itemId);
      if (!item || line.expectedPrice !== item.price) {
        fields[line.itemId] = "El precio guardado cambió. Quita el artículo y agrégalo de nuevo.";
      } else if (Number(line.quantity) > estimated(item, queue, sale.branchId, snapshot.issuedAt)) {
        fields[line.itemId] = "Saldo estimado insuficiente para esta cantidad.";
      } else {
        proofs[line.itemId] = item.proof;
        items[line.itemId] = item.article.name;
      }
    }
    if (Object.keys(fields).length) throw new ApiError(409, "OFFLINE_LINES", "Revisa los artículos marcados.", fields);
    await db.queue.add({ key, owner, branch: snapshot.branch, soldAt: now.toISOString(), sale, snapshotId: snapshot.id, proofs, items });
    return sale.requestKey;
  }).then(id => { notify(); return id; });
}

async function runSync(owner: string) {
  await ensureCsrf();
  if (ownerKey(await authApi.me()) !== owner) return;
  for (const row of (await pendingSales(owner)).sort((a, b) => a.soldAt.localeCompare(b.soldAt))) {
    try {
      const result = await request<Entry>(api + "/queue", {
        method: "POST",
        body: JSON.stringify({ snapshotId: row.snapshotId, soldAt: row.soldAt, sale: row.sale, proofs: row.proofs }),
      });
      await db.queue.update(row.key, { result, error: undefined, settledAt: new Date().toISOString() });
    } catch (error) {
      if (!(error instanceof ApiError) || error.status === 0 || error.status >= 500) throw error;
      await db.queue.update(row.key, { error: error.message });
      if (error.status === 401 || error.status === 403) break;
    } finally { notify(); }
  }
  const review = (await db.queue.where("owner").equals(owner).toArray()).filter(row => row.result?.state === "REVIEW");
  for (const row of review) {
    try {
      const result = await request<Entry>(api + "/queue/" + encodeURIComponent(row.sale.requestKey));
      await db.queue.update(row.key, { result, settledAt: new Date().toISOString() });
    } catch (error) {
      if (!(error instanceof ApiError) || error.status === 0 || error.status >= 500) throw error;
    }
  }
  notify();
}

export function syncOffline(owner: string) {
  const current = localQueues.get(owner);
  if (current) return current;
  const work = Promise.resolve(navigator.locks ? navigator.locks.request("stockly:sales:" + owner, () => runSync(owner)) : runSync(owner))
    .finally(() => localQueues.delete(owner));
  localQueues.set(owner, work);
  return work;
}

export function useOfflineSales(owner: string, enabled: boolean) {
  const [state, setState] = useState<{ owner: string; branches: CachedBranch[]; queue: QueuedSale[] }>();
  const [connected, setConnected] = useState(navigator.onLine);
  const [shellReady, setShellReady] = useState(false);
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    const update = () => setShellReady(!!navigator.serviceWorker.controller);
    update();
    navigator.serviceWorker.addEventListener("controllerchange", update);
    return () => navigator.serviceWorker.removeEventListener("controllerchange", update);
  }, []);
  const [problem, setProblem] = useState("");
  useEffect(() => {
    if (!owner) return;
    let active = true;
    const read = () => {
      Promise.all([db.branches.where("owner").equals(owner).toArray(), db.queue.where("owner").equals(owner).toArray()])
        .then(([branches, queue]) => { if (active) setState({ owner, branches, queue }); })
        .catch(() => { if (active) setProblem("El navegador no pudo abrir el almacenamiento para ventas sin internet."); });
    };
    const online = async () => {
      setConnected(true);
      setProblem("");
      if (!enabled) return;
      try { await syncOffline(owner); await prepare(owner); }
      catch (failure) {
        if (!active) return;
        if (failure instanceof ApiError && failure.status === 0) setConnected(false);
        else setProblem(failure instanceof Error ? failure.message : "No se pudo preparar este equipo.");
      }
    };
    const offline = () => setConnected(false);
    read();
    if (navigator.onLine) void online();
    const refresh = window.setInterval(() => { if (navigator.onLine) void online(); else read(); }, 5 * 60 * 1000);
    window.addEventListener("online", online);
    window.addEventListener("offline", offline);
    window.addEventListener(eventName, read);
    return () => {
      active = false;
      window.clearInterval(refresh);
      window.removeEventListener("online", online);
      window.removeEventListener("offline", offline);
      window.removeEventListener(eventName, read);
    };
  }, [owner, enabled]);
  return {
    branches: state?.owner === owner ? state.branches : [],
    queue: state?.owner === owner ? state.queue : [],
    connected, problem, shellReady,
  };
}

export type OfflineState = ReturnType<typeof useOfflineSales>;

export function listReview() { return request<Entry[]>(api + "/review"); }
export function resolveSale(id: string, action: "retry" | "close", details?: { reason: string; refundDetail: string }) {
  return request<Entry>(api + "/review/" + encodeURIComponent(id) + "/" + action, {
    method: "POST", body: details ? JSON.stringify(details) : undefined,
  });
}
