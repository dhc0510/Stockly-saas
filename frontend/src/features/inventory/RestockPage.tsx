import { useCallback, useEffect, useMemo, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { ClipboardList, Download, Printer, Search, Settings2 } from "lucide-react";
import { ApiError } from "../../lib/http";
import { useAuth } from "../auth/AuthContext";
import { inventoryApi, type Branch, type PausedRow, type Restock, type RestockRow, type StockItem } from "./api";
import { ThresholdDialog } from "./InventoryPages";
import { restockChanged } from "./restock";
import "./restock.css";

// STK-016 · Por reponer: agotados y en mínimo por sucursal, con «A pedir», exportación e impresión.
const quantity = (value:number|null|undefined, unit:string) => `${Number(value ?? 0).toLocaleString("es-CR", { minimumFractionDigits: 0, maximumFractionDigits: 3 })} ${unit}`;
const stateLabel: Record<RestockRow["state"], string> = { EXHAUSTED: "Agotado", MINIMUM: "Stock mínimo", LOW: "Stock bajo" };
const stateClass: Record<RestockRow["state"], string> = { EXHAUSTED: "out", MINIMUM: "min", LOW: "low" };
const crTime = new Intl.DateTimeFormat("es-CR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Costa_Rica" });
const crDate = new Intl.DateTimeFormat("es-CR", { dateStyle: "short", timeZone: "America/Costa_Rica" });
const label = (row:{productName:string; variantName:string|null}) => row.productName + (row.variantName ? ` · ${row.variantName}` : "");
const message = (error:unknown) => error instanceof Error ? error.message : "No se pudo completar la operación.";

function basisText(row:RestockRow) {
  if (row.basis === "TARGET") return `hasta el nivel deseado (${quantity(row.targetQuantity, row.unit)})`;
  return `hasta salir de alerta (mínimo + margen = ${quantity(Number(row.minimumQuantity) + Number(row.recoveryMargin), row.unit)})`;
}

/** Contador del menú; se actualiza al navegar, al cambiar la configuración y cada minuto. */
export function RestockNavLink({ onClick }:{ onClick:()=>void }) {
  const { pathname } = useLocation(); const [count, setCount] = useState<number>();
  useEffect(() => {
    let alive = true;
    const load = () => inventoryApi.restockCount().then(r => { if (alive) setCount(r.count); }).catch(() => { if (alive) setCount(undefined); });
    load(); const timer = window.setInterval(load, 60_000); window.addEventListener("stockly:restock-changed", load);
    return () => { alive = false; window.clearInterval(timer); window.removeEventListener("stockly:restock-changed", load); };
  }, [pathname]);
  return <NavLink className="identity-nav restock-nav" to="/inventory/restock" onClick={onClick}>
    <ClipboardList size={18}/> Por reponer{count ? <span className="restock-nav-count" aria-label={`${count} por reponer`}>{count > 99 ? "99+" : count}</span> : null}
  </NavLink>;
}

export function RestockPage({ canEdit }:{ canEdit:boolean }) {
  const { user } = useAuth();
  const [branches, setBranches] = useState<Branch[]>(); const [branchId, setBranchId] = useState<string>();
  const [search, setSearch] = useState(""); const [term, setTerm] = useState(""); const [category, setCategory] = useState(""); const [includeLow, setIncludeLow] = useState(false);
  const [data, setData] = useState<Restock>(); const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState<unknown>(); const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<{ row:StockItem; branchId:string }>(); const [toast, setToast] = useState(""); const [menu, setMenu] = useState(false);
  const [printing, setPrinting] = useState(false); const [busy, setBusy] = useState(false);

  useEffect(() => { inventoryApi.branches(false).then(list => { const active = list.filter(b => b.active); setBranches(active); setBranchId(active.length === 1 ? active[0].id : ""); }).catch(e => { setError(e); setLoading(false); }); }, []);
  useEffect(() => { const timer = window.setTimeout(() => setTerm(search.trim()), 250); return () => window.clearTimeout(timer); }, [search]);
  const params = useMemo(() => { const p = new URLSearchParams(); if (branchId) p.set("branchId", branchId); if (term) p.set("search", term); if (category) p.set("category", category); if (includeLow) p.set("includeLow", "true"); return p; }, [branchId, term, category, includeLow]);
  useEffect(() => {
    if (branchId === undefined) return;
    let alive = true; setLoading(true); setError(undefined);
    inventoryApi.restock(params).then(result => { if (!alive) return; setData(result);
      if (!term && !category) setCategories([...new Set([...result.rows.map(r => r.category)].filter((c):c is string => !!c))].sort((a, b) => a.localeCompare(b, "es"))); })
      .catch(e => { if (alive) { setError(e); setData(undefined); } }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [params, branchId, term, category, reload]);
  useEffect(() => { if (!toast) return; const t = window.setTimeout(() => setToast(""), 3000); return () => window.clearTimeout(t); }, [toast]);

  const branchName = branchId ? branches?.find(b => b.id === branchId)?.name ?? "" : "Todas mis sucursales";
  const all = !branchId;
  const rows = data?.rows ?? [];
  const failed = !!error && !data;

  const loadItem = useCallback(async (row:{ branchId:string; itemId:string; sku:string }) => {
    const items = await inventoryApi.items(new URLSearchParams({ branchId: row.branchId, search: row.sku }));
    const item = items.find(i => i.itemId === row.itemId && i.branchId === row.branchId);
    if (!item) throw new Error("No se encontró el producto en esta sucursal.");
    return item;
  }, []);
  async function configure(row:{ branchId:string; itemId:string; sku:string }) { try { setEditing({ row: await loadItem(row), branchId: row.branchId }); } catch (e) { setToast(message(e)); } }
  async function resume(row:PausedRow) {
    setBusy(true);
    try { const item = await loadItem(row);
      await inventoryApi.updateThresholds(row.branchId, row.itemId, { minimumQuantity: item.minimumQuantity, lowThreshold: item.lowThreshold, recoveryMargin: item.recoveryMargin,
        exhaustedBasis: item.exhaustedBasis, targetQuantity: item.targetQuantity, restockPaused: false });
      setToast(`${label(row)} vuelve a reponerse.`); setReload(v => v + 1); restockChanged();
    } catch (e) { setToast(message(e)); } finally { setBusy(false); }
  }
  async function download(format:"xlsx"|"csv") {
    setMenu(false); setBusy(true);
    try {
      const p = new URLSearchParams(params); p.set("format", format);
      const response = await fetch(`/api/inventory/restock/export?${p}`, { credentials: "same-origin", cache: "no-store" });
      if (!response.ok) { const body = await response.json().catch(() => ({})); throw new ApiError(response.status, body.code ?? "EXPORT_FAILED", body.message ?? "No pudimos generar el archivo."); }
      const blob = await response.blob();
      const name = /filename="?([^";]+)"?/.exec(response.headers.get("Content-Disposition") ?? "")?.[1] ?? `por-reponer.${format}`;
      const url = URL.createObjectURL(blob); const a = document.createElement("a"); a.href = url; a.download = name; document.body.append(a); a.click(); a.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setToast(`Descargando ${name}`);
    } catch (e) { setToast(e instanceof ApiError && e.status !== 0 ? e.message : "No pudimos generar el archivo. Revisa la conexión e intenta de nuevo."); }
    finally { setBusy(false); }
  }

  const filters = [branchName, includeLow ? "Agotados, en mínimo y stock bajo" : "Agotados y en mínimo", category && `Categoría: ${category}`, term && `Búsqueda: «${term}»`].filter(Boolean).join(" · ");
  const order = (row:RestockRow, compact = false) => row.toOrder == null
    ? <><span className="restock-missing">Sin dato para sugerir</span>{canEdit ? <button type="button" className="inventory-text-button" onClick={() => void configure(row)}>Definir nivel deseado</button> : <small>Falta mínimo o nivel deseado</small>}</>
    : row.toOrder === 0 ? <><span className="restock-order">—</span><small>No hace falta pedir todavía</small></>
    : <><span className="restock-order">{quantity(row.toOrder, row.unit)}</span>{!compact && <small>{basisText(row)}</small>}</>;

  if (printing && data) return <section className="restock-page">
    <div className="page-heading restock-print-actions"><div><p className="eyebrow">INVENTARIO / POR REPONER</p><h1>Vista para imprimir</h1><p className="subtitle">Se abre el cuadro de impresión del navegador; también permite guardar como PDF.</p></div>
      <div className="restock-actions"><button type="button" className="button secondary" onClick={() => setPrinting(false)}>Volver a la lista</button><button type="button" className="button primary" onClick={() => window.print()}><Printer size={16}/> Imprimir</button></div></div>
    <article className="restock-sheet">
      <header><div><strong>{data.companyName}</strong><h2>Productos por reponer</h2><p>{filters}</p></div><p>{crTime.format(new Date())}<br/>Generado por {user?.fullName}</p></header>
      <table><thead><tr>{all && <th>Sucursal</th>}<th>Producto</th><th>SKU</th><th>Estado</th><th className="num">Disponible</th><th className="num">A pedir</th><th>Pedido</th></tr></thead>
        <tbody>{rows.map(r => <tr key={r.branchId + r.itemId}>{all && <td>{r.branchName}</td>}<td>{label(r)}</td><td>{r.sku}</td><td>{stateLabel[r.state]}</td><td className="num">{quantity(r.available, r.unit)}</td><td className="num"><strong>{r.toOrder == null ? "Sin dato" : r.toOrder === 0 ? "—" : quantity(r.toOrder, r.unit)}</strong></td><td><span className="restock-blank"/></td></tr>)}</tbody></table>
      <footer>{rows.length} productos · «A pedir» = nivel deseado − disponible, o hasta salir de alerta si no hay nivel deseado. La columna «Pedido» queda en blanco para anotar a mano.</footer>
    </article>
  </section>;

  return <section className="restock-page">
    <div className="page-heading"><div><p className="eyebrow">INVENTARIO / REPOSICIÓN</p><h1>Por reponer</h1><p className="subtitle">Productos agotados y en su mínimo, ordenados por urgencia, con la cantidad sugerida para pedir.</p></div>
      <div className="restock-actions">
        <label className="inventory-branch-select"><span>Sucursal</span><select value={branchId ?? ""} onChange={e => setBranchId(e.target.value)} disabled={!branches}>
          {(branches?.length ?? 0) !== 1 && <option value="">Todas mis sucursales</option>}{branches?.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select></label>
        <div className="restock-menu-wrap"><button type="button" className="button secondary" disabled={failed || loading || busy || rows.length === 0} aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu(v => !v)}><Download size={16}/> Exportar</button>
          {menu && <div className="restock-menu" role="menu"><button role="menuitem" type="button" onClick={() => void download("xlsx")}>Excel (.xlsx)<small>Para editar y enviar al proveedor</small></button><button role="menuitem" type="button" onClick={() => void download("csv")}>CSV<small>Abre en Excel, Google Sheets u otros</small></button></div>}</div>
        <button type="button" className="button secondary" disabled={failed || loading || rows.length === 0} onClick={() => setPrinting(true)}><Printer size={16}/> Imprimir</button>
      </div></div>

    {failed ? <div className="state-panel restock-state error" role="alert"><h2>No pudimos cargar la lista</h2><p>{message(error)} No mostramos cantidades anteriores para no inducir a error.</p><button type="button" className="button secondary" onClick={() => setReload(v => v + 1)}>Reintentar</button></div> : <>
      <div className="restock-summary">
        <div><span className="restock-dot out"/><div><strong>{data?.totals.exhausted ?? "–"}</strong><span>Agotados</span><small>Reponer primero</small></div></div>
        <div><span className="restock-dot min"/><div><strong>{data?.totals.minimum ?? "–"}</strong><span>En mínimo</span><small>Avisan en la campanita</small></div></div>
        <div className={includeLow ? "" : "off"}><span className="restock-dot low"/><div><strong>{data?.totals.low ?? "–"}</strong><span>Stock bajo</span><small>{includeLow ? "Incluidos en la lista" : "Ocultos · usa el filtro"}</small></div></div>
      </div>
      <div className="restock-toolbar">
        <label className="inventory-search"><Search size={17}/><input type="search" aria-label="Buscar producto" placeholder="Nombre, SKU o código" value={search} onChange={e => setSearch(e.target.value)}/></label>
        <select aria-label="Categoría" value={category} onChange={e => setCategory(e.target.value)}><option value="">Todas las categorías</option>{categories.map(c => <option key={c}>{c}</option>)}</select>
        <label className="inventory-check restock-switch"><input type="checkbox" checked={includeLow} onChange={e => setIncludeLow(e.target.checked)}/><span>Incluir stock bajo</span></label>
      </div>
      {loading && !data ? <div className="state-panel" role="status">Cargando productos por reponer…</div>
        : rows.length === 0 ? <div className="state-panel restock-state" role="status"><h2>{term || category ? "No hay resultados" : `Todo al día${branchId ? ` en ${branchName}` : ""}`}</h2><p>{term || category ? "Ajusta la búsqueda o la categoría." : includeLow ? "Ningún producto está agotado, en su mínimo ni con stock bajo." : "Ningún producto está agotado ni en su mínimo. Activa «Incluir stock bajo» para adelantar pedidos."}</p></div>
        : <div className="restock-list" aria-busy={loading}>
          <table className="restock-table"><thead><tr><th>Producto</th>{all && <th>Sucursal</th>}<th>Estado</th><th className="num">Disponible</th><th className="num">Mínimo</th><th className="num">Nivel deseado</th><th className="num">A pedir</th>{canEdit && <th><span className="sr-only">Acciones</span></th>}</tr></thead>
            <tbody>{rows.map(r => <tr key={r.branchId + r.itemId}>
              <td><strong>{label(r)}</strong><small>SKU {r.sku}{r.category ? ` · ${r.category}` : ""}</small></td>{all && <td>{r.branchName}</td>}
              <td><span className={`restock-badge ${stateClass[r.state]}`}>{stateLabel[r.state]}</span></td>
              <td className="num">{quantity(r.available, r.unit)}</td><td className="num">{r.minimumQuantity == null ? <small>Sin configurar</small> : quantity(r.minimumQuantity, r.unit)}</td>
              <td className="num">{r.targetQuantity == null ? <small>Sin definir</small> : quantity(r.targetQuantity, r.unit)}</td><td className="num restock-order-cell">{order(r)}</td>
              {canEdit && <td><button type="button" className="inventory-text-button" aria-label={`Configurar reposición de ${label(r)}`} onClick={() => void configure(r)}><Settings2 size={14}/> Configurar</button></td>}</tr>)}</tbody></table>
          <div className="restock-cards">{rows.map(r => <article key={r.branchId + r.itemId} className="restock-card">
            <div className="restock-card-head"><div><strong>{label(r)}</strong><small>SKU {r.sku}{all ? ` · ${r.branchName}` : ""}</small></div><span className={`restock-badge ${stateClass[r.state]}`}>{stateLabel[r.state]}</span></div>
            <dl><div><dt>Disponible</dt><dd>{quantity(r.available, r.unit)}</dd></div><div><dt>Mínimo</dt><dd>{r.minimumQuantity == null ? "—" : quantity(r.minimumQuantity, r.unit)}</dd></div><div><dt>Deseado</dt><dd>{r.targetQuantity == null ? "—" : quantity(r.targetQuantity, r.unit)}</dd></div></dl>
            <div className="restock-card-foot"><div className="restock-order-cell">{order(r, false)}</div>{canEdit && r.toOrder != null && <button type="button" className="inventory-text-button" onClick={() => void configure(r)}>Configurar</button>}</div>
          </article>)}</div>
        </div>}
      {data && data.paused.length > 0 && <details className="restock-paused"><summary>No se reponen{branchId ? ` en ${branchName}` : ""} ({data.paused.length})</summary>
        {data.paused.map(p => <div className="restock-paused-row" key={p.branchId + p.itemId}><div><strong>{label(p)}</strong><small>SKU {p.sku}{all ? ` · ${p.branchName}` : ""} · Disponible {quantity(p.available, p.unit)}{p.pausedAt ? ` · Marcado${p.pausedBy ? ` por ${p.pausedBy}` : ""} el ${crDate.format(new Date(p.pausedAt))}` : ""}</small></div>
          {canEdit ? <button type="button" className="button secondary" disabled={busy} onClick={() => void resume(p)}>Volver a reponer</button> : <span className="inventory-paused-badge">No se repone</span>}</div>)}
        <p className="restock-note">Estos productos no avisan en la campanita ni aparecen arriba. En Existencias se ven con la etiqueta «No se repone».</p></details>}
      <p className="restock-note">«A pedir» es una sugerencia: nivel deseado − disponible. Si no hay nivel deseado, sugiere lo necesario para salir de alerta (mínimo + margen de recuperación). No crea pedidos ni mueve inventario.</p>
    </>}
    {toast && <div className="restock-toast" role="status">{toast}</div>}
    {editing && <ThresholdDialog row={editing.row} branchId={editing.branchId} onClose={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); setToast("Cambios guardados."); setReload(v => v + 1); restockChanged(); }}/>}
  </section>;
}
