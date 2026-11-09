import { useEffect, useMemo, useRef, useState } from "react";
import { Download, Printer, Search } from "lucide-react";
import { ApiError } from "../../lib/http";
import { useAuth } from "../auth/AuthContext";
import type { SessionUser } from "../auth/api";
import { reportsApi, type Bucket, type Idle, type Kpis, type Payments, type ProductRow, type Products, type ReportContext,
  type ReportData, type ReportTab, type Sellers, type Summary } from "./api";
import { periodError, presetRange, presets, todayCR, variation, type Preset } from "./period";
import "./reports.css";

// STK-017 · Reportes operativos (mockup aprobado docs/06-Mockups/STK-017).
export const reportsAccess = (user:SessionUser) => user.role === "COMPANY_ADMIN" ||
  user.modules.some(m => m.moduleKey === "REPORTS" && m.accessLevel !== "NONE");

const tabs:{ key:ReportTab; label:string }[] = [
  { key: "summary", label: "Resumen" }, { key: "products", label: "Productos" }, { key: "payments", label: "Cobros" },
  { key: "sellers", label: "Vendedores" }, { key: "idle", label: "Sin ventas" },
];
const money = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC", minimumFractionDigits: 0, maximumFractionDigits: 2 });
const wholeMoney = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC", maximumFractionDigits: 0 });
const number = new Intl.NumberFormat("es-CR", { maximumFractionDigits: 3 });
const percent = new Intl.NumberFormat("es-CR", { style: "percent", minimumFractionDigits: 1, maximumFractionDigits: 1 });
const crTime = new Intl.DateTimeFormat("es-CR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Costa_Rica" });
const crDate = new Intl.DateTimeFormat("es-CR", { dateStyle: "short", timeZone: "America/Costa_Rica" });
const day = (iso:string, options:Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat("es-CR", { ...options, timeZone: "UTC" }).format(new Date(iso + "T00:00:00Z"));
const shortDate = (iso:string) => day(iso, { day: "2-digit", month: "2-digit", year: "numeric" });
const bucketLabel = (b:Bucket, monthly:boolean) => monthly ? day(b.start, { month: "short", year: "numeric" }) : day(b.start, { weekday: "short", day: "2-digit" });
const bucketLong = (b:Bucket, monthly:boolean) => monthly ? day(b.start, { month: "long", year: "numeric" }) : day(b.start, { weekday: "long", day: "numeric", month: "long" });
const minus = (v:number) => v ? `−${money.format(v)}` : "—";
const label = (r:{ productName:string; variantName:string|null }) => r.productName + (r.variantName ? ` · ${r.variantName}` : "");
const message = (e:unknown) => e instanceof Error ? e.message : "No se pudo completar la operación.";

export function ReportsPage() {
  const { user } = useAuth();
  const today = todayCR();
  const [preset, setPreset] = useState<Preset>("last7");
  const [range, setRange] = useState(() => presetRange("last7", today));
  const [draft, setDraft] = useState(range);
  const [branchId, setBranchId] = useState<string>();
  const [tab, setTab] = useState<ReportTab>("summary");
  const [search, setSearch] = useState(""); const [term, setTerm] = useState("");
  const [category, setCategory] = useState(""); const [sort, setSort] = useState("AMOUNT"); const [onlyStock, setOnlyStock] = useState(true);
  const [data, setData] = useState<Partial<ReportData>>({}); const [context, setContext] = useState<ReportContext>();
  const [loading, setLoading] = useState(true); const [error, setError] = useState<unknown>(); const [reload, setReload] = useState(0);
  const [menu, setMenu] = useState(false); const [busy, setBusy] = useState(false); const [toast, setToast] = useState("");
  const [printing, setPrinting] = useState<{ summary:Summary; payments:Payments; products:Products }>();

  const draftError = preset === "custom" ? periodError(draft.from, draft.to, today) : null;
  useEffect(() => { const t = window.setTimeout(() => setTerm(search.trim()), 250); return () => window.clearTimeout(t); }, [search]);
  useEffect(() => { if (!toast) return; const t = window.setTimeout(() => setToast(""), 3000); return () => window.clearTimeout(t); }, [toast]);
  useEffect(() => { setSearch(""); setTerm(""); setCategory(""); }, [tab]);

  const params = useMemo(() => {
    const p = new URLSearchParams({ from: range.from, to: range.to });
    if (branchId) p.set("branchId", branchId);
    if (tab === "products") { if (term) p.set("search", term); if (category) p.set("category", category); p.set("sort", sort); }
    if (tab === "idle") { if (term) p.set("search", term); p.set("onlyStock", String(onlyStock)); }
    return p;
  }, [range, branchId, tab, term, category, sort, onlyStock]);

  useEffect(() => {
    let alive = true; setLoading(true); setError(undefined);
    reportsApi.load(tab, params).then(result => {
      if (!alive) return;
      setData(old => ({ ...old, [tab]: result })); setContext(result.context);
      if (branchId === undefined) setBranchId(result.context.branches.length === 1 ? result.context.branches[0].id : "");
    }).catch(e => { if (alive) { setError(e); setData(old => ({ ...old, [tab]: undefined })); } })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [tab, params, reload]);

  function choosePreset(next:Preset) {
    setPreset(next);
    if (next === "custom") { setDraft(range); return; }
    const r = presetRange(next, today); setRange(r); setDraft(r);
  }
  async function download(format:"xlsx"|"csv") {
    setMenu(false); setBusy(true);
    try { const p = new URLSearchParams(params); p.set("format", format); p.set("report", tab); setToast(`Descargando ${await reportsApi.download(p)}`); }
    catch (e) { setToast(e instanceof ApiError && e.status !== 0 ? e.message : "No pudimos generar el archivo. Revisa la conexión e intenta de nuevo."); }
    finally { setBusy(false); }
  }
  async function print() {
    setBusy(true);
    try {
      const base = new URLSearchParams({ from: range.from, to: range.to }); if (branchId) base.set("branchId", branchId);
      const products = new URLSearchParams(base); products.set("sort", "AMOUNT");
      const [summary, payments, top] = await Promise.all([reportsApi.load("summary", base), reportsApi.load("payments", base), reportsApi.load("products", products)]);
      setPrinting({ summary, payments, products: top });
    } catch (e) { setToast(message(e)); } finally { setBusy(false); }
  }

  const current = data[tab];
  const invalidPeriod = error instanceof ApiError && error.code === "REPORT_INVALID";
  const failed = !!error && !invalidPeriod;
  const branches = context?.branches ?? [];
  const period = `${shortDate(range.from)} al ${shortDate(range.to)}`;
  const place = branchId ? branches.find(b => b.id === branchId)?.name ?? context?.branchName ?? "" : "Todas mis sucursales";
  const blocked = loading || failed || invalidPeriod || !!draftError || !current || busy;

  if (printing) return <PrintSheet data={printing} who={user?.fullName ?? ""} place={place} period={period} onBack={() => setPrinting(undefined)}/>;

  return <section className="reports-page">
    <div className="page-heading"><div><p className="eyebrow">OPERACIÓN COMERCIAL</p><h1>Reportes</h1>
      <p className="subtitle">Ventas, cobros y productos del período, por sucursal. {period} · hora de Costa Rica.{branches.length === 1 ? " Solo ves la sucursal que tienes asignada." : ""}</p></div>
      <div className="reports-actions">
        <div className="reports-menu-wrap"><button type="button" className="button secondary" disabled={blocked} aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu(v => !v)}><Download size={16}/> Exportar</button>
          {menu && <div className="reports-menu" role="menu">
            <button role="menuitem" type="button" onClick={() => void download("xlsx")}>Excel (.xlsx)<small>Una hoja por reporte</small></button>
            <button role="menuitem" type="button" onClick={() => void download("csv")}>CSV<small>Solo la pestaña «{tabs.find(t => t.key === tab)?.label}»</small></button></div>}</div>
        <button type="button" className="button secondary" disabled={blocked} onClick={() => void print()}><Printer size={16}/> Imprimir</button>
      </div></div>

    <div className="reports-filters">
      <div><span className="sr-only" id="reports-period">Período</span><div className="reports-chips" role="group" aria-labelledby="reports-period">
        {presets.map(p => <button key={p.key} type="button" aria-pressed={preset === p.key} onClick={() => choosePreset(p.key)}>{p.label}</button>)}</div></div>
      {preset === "custom" && <form className="reports-range" onSubmit={e => { e.preventDefault(); if (!draftError) setRange(draft); }}>
        <label>Desde<input type="date" max={today} value={draft.from} aria-invalid={!!draftError} aria-describedby={draftError ? "reports-range-error" : undefined} onChange={e => setDraft(d => ({ ...d, from: e.target.value }))}/></label>
        <label>Hasta<input type="date" max={today} value={draft.to} aria-invalid={!!draftError} onChange={e => setDraft(d => ({ ...d, to: e.target.value }))}/></label>
        <button type="submit" className="button primary" disabled={!!draftError}>Aplicar</button>
        {(draftError || invalidPeriod) && <span className="reports-field-error" id="reports-range-error" role="alert">{draftError ?? message(error)}</span>}
      </form>}
      <label className="reports-branch">Sucursal<select value={branchId ?? ""} disabled={branches.length <= 1} onChange={e => setBranchId(e.target.value)}>
        {branches.length !== 1 && <option value="">Todas mis sucursales</option>}{branches.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select>
        {branches.length === 1 && <small>Solo tienes acceso a esta sucursal</small>}</label>
    </div>

    {invalidPeriod && preset !== "custom" ? <div className="state-panel reports-state" role="alert"><h2>Revisa el período</h2><p>{message(error)}</p></div>
      : draftError ? <div className="state-panel reports-state" role="status"><h2>Corrige las fechas para ver los reportes</h2><p>El rango puede ser de hasta 366 días.</p></div>
      : failed ? <div className="state-panel reports-state error" role="alert"><h2>No pudimos cargar los reportes</h2><p>{message(error)} No mostramos cifras incompletas.</p>
          <button type="button" className="button secondary" onClick={() => setReload(v => v + 1)}>Reintentar</button></div>
      : <>
        <label className="reports-tab-select">Ver reporte<select value={tab} onChange={e => setTab(e.target.value as ReportTab)}>{tabs.map(t => <option key={t.key} value={t.key}>{t.label}</option>)}</select></label>
        <div className="reports-tabs" role="tablist" aria-label="Reportes">{tabs.map(t => <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>{t.label}</button>)}</div>
        {!current ? <Loading/> : <div aria-busy={loading} className={loading ? "reports-busy" : undefined}>
          {tab === "summary" && <SummaryView data={current as Summary} onProducts={() => setTab("products")}/>}
          {tab === "products" && <ProductsView data={current as Products} search={search} setSearch={setSearch} category={category} setCategory={setCategory} sort={sort} setSort={setSort} filtered={!!term || !!category}/>}
          {tab === "payments" && <PaymentsView data={current as Payments}/>}
          {tab === "sellers" && <SellersView data={current as Sellers}/>}
          {tab === "idle" && <IdleView data={current as Idle} search={search} setSearch={setSearch} onlyStock={onlyStock} setOnlyStock={setOnlyStock} all={!branchId} filtered={!!term}/>}
        </div>}
      </>}
    {toast && <div className="reports-toast" role="status">{toast}</div>}
  </section>;
}

function Loading() {
  return <div aria-busy="true"><span className="sr-only" role="status">Cargando reportes…</span>
    <div className="reports-kpis">{[0, 1, 2, 3].map(i => <div key={i} className="reports-kpi"><div className="reports-skel" style={{ height: 14, width: "60%" }}/><div className="reports-skel" style={{ height: 26, width: "80%" }}/></div>)}</div>
    <div className="reports-panel"><div className="reports-skel" style={{ height: 200 }}/></div></div>;
}

function Delta({ current, previous, invert = false }:{ current:number; previous:number; invert?:boolean }) {
  const v = variation(current, previous);
  if (v === null) return <span className="reports-delta flat">Sin datos del período anterior</span>;
  const tone = v === 0 ? "flat" : (v > 0) !== invert ? "up" : "down";
  return <span className={`reports-delta ${tone}`}>{v > 0 ? "▲" : v < 0 ? "▼" : "="} {Math.abs(v)} % vs. período anterior</span>;
}

function Empty({ text }:{ text:string }) {
  return <div className="state-panel reports-state" role="status"><h2>No hay ventas en este período</h2><p>{text}</p></div>;
}

function SummaryView({ data, onProducts }:{ data:Summary; onProducts:()=>void }) {
  const a = data.current, b = data.previous;
  if (!a.sales && !a.returns && !a.laterVoids && !a.sameDayVoids)
    return <Empty text="No se registraron ventas, devoluciones ni anulaciones. Elige otro período o sucursal."/>;
  return <>
    <div className="reports-kpis">
      <Kpi title="Ventas netas" value={money.format(a.net)}><Delta current={a.net} previous={b.net}/></Kpi>
      <Kpi title="Ventas realizadas" value={String(a.sales)}><Delta current={a.sales} previous={b.sales}/></Kpi>
      <Kpi title="Venta promedio" value={wholeMoney.format(a.average)}><Delta current={a.average} previous={b.average}/></Kpi>
      <Kpi title="Devoluciones" value={money.format(a.returns)}><Delta current={a.returns} previous={b.returns} invert/></Kpi>
    </div>
    <div className="reports-grid">
      <section className="reports-panel"><div className="reports-panel-head"><h2>Ventas netas por {data.context.grouping === "MONTH" ? "mes" : "día"}</h2><small>Pasa el cursor o enfoca una barra</small></div>
        <Chart series={data.series} monthly={data.context.grouping === "MONTH"}/></section>
      <section className="reports-panel"><div className="reports-panel-head"><h2>Cómo se forma</h2></div>
        <Breakdown k={a}/>
        <p className="reports-note">Las devoluciones y las anulaciones de ventas de otros días restan el día en que se hicieron. Las anuladas el mismo día no cuentan.</p>
        {data.top.length > 0 && <><div className="reports-panel-head reports-top"><h2>Más vendidos</h2><button type="button" className="inventory-text-button" onClick={onProducts}>Ver todos</button></div>
          <div className="reports-lines">{data.top.map((p, i) => <div key={p.itemId}><span>{i + 1}. {label(p)}</span><span className="num">{money.format(p.amount)}</span></div>)}</div></>}
      </section>
    </div>
  </>;
}

function Breakdown({ k }:{ k:Kpis }) {
  return <div className="reports-lines">
    <div><span>Ventas brutas</span><span className="num">{money.format(k.gross)}</span></div>
    <div><span>Devoluciones del período</span><span className="num">{minus(k.returns)}</span></div>
    <div><span>Anulaciones de ventas anteriores</span><span className="num">{minus(k.laterVoids)}</span></div>
    <div className="total"><span>Ventas netas</span><span className="num">{money.format(k.net)}</span></div>
    <div className="sub"><span>Anuladas el mismo día <span className="reports-badge">No suman</span></span><span className="num">{k.sameDayVoids} · {money.format(k.sameDayVoidAmount)}</span></div>
  </div>;
}

function Kpi({ title, value, children }:{ title:string; value:string; children:React.ReactNode }) {
  return <div className="reports-kpi"><span>{title}</span><strong>{value}</strong>{children}</div>;
}

/** Barras de una sola serie (ventas netas) con base en cero; admite netos negativos. */
function Chart({ series, monthly }:{ series:Bucket[]; monthly:boolean }) {
  const [hover, setHover] = useState<number>(); const box = useRef<HTMLDivElement>(null);
  const W = 640, H = 220, L = 64, B = 26, T = 10;
  const values = series.map(b => b.net);
  const top = nice(Math.max(0, ...values)), bottom = -nice(Math.max(0, ...values.map(v => -v)));
  const span = top - bottom || 1;
  const y = (v:number) => T + (H - T - B) * (top - v) / span;
  const bw = (W - L) / Math.max(1, series.length);
  const every = Math.ceil(series.length / (W / 70));
  const ticks = [0, 0.25, 0.5, 0.75, 1].map(f => bottom + span * f);
  const tip = hover === undefined ? undefined : series[hover];
  return <div className="reports-chart" ref={box}>
    <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Ventas netas por ${monthly ? "mes" : "día"}`}>
      {ticks.map(t => <g key={t}><line className="gl" x1={L} x2={W} y1={y(t)} y2={y(t)}/><text className="ax" x={L - 8} y={y(t) + 4} textAnchor="end">{axis(t)}</text></g>)}
      {series.map((b, i) => { const x = L + i * bw + bw * 0.2, w = Math.max(2, bw * 0.6), y0 = y(0), y1 = y(b.net), h = Math.abs(y0 - y1);
        return <g key={b.start} tabIndex={0} aria-label={`${bucketLong(b, monthly)}: ventas netas ${money.format(b.net)}, ${b.sales} ventas`}
          onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(undefined)} onFocus={() => setHover(i)} onBlur={() => setHover(undefined)}>
          <rect className="hit" x={L + i * bw} y={T} width={bw} height={H - T - B}/>
          {h > 0 && <rect className={`bar${hover === i ? " on" : ""}`} x={x} y={Math.min(y0, y1)} width={w} height={h} rx={Math.min(4, w / 2)}/>}
          {i % every === 0 && <text className="ax" x={x + w / 2} y={H - 8} textAnchor="middle">{bucketLabel(b, monthly)}</text>}
        </g>; })}
    </svg>
    {tip && hover !== undefined && <div className="reports-tip" style={{ left: `${(L + hover * bw + bw / 2) / W * 100}%` }}>
      <strong>{bucketLong(tip, monthly)}</strong>Netas {money.format(tip.net)} · {tip.sales} ventas{tip.returns ? <><br/>Devoluciones {minus(tip.returns)}</> : null}{tip.voids ? <><br/>Anulaciones {minus(tip.voids)}</> : null}</div>}
    <details className="reports-table-toggle"><summary>Ver como tabla</summary>
      <div className="reports-scroll"><table className="reports-table"><thead><tr><th>{monthly ? "Mes" : "Día"}</th><th className="num">Ventas</th><th className="num">Brutas</th><th className="num">Devoluciones</th><th className="num">Anulaciones</th><th className="num">Netas</th></tr></thead>
        <tbody>{series.map(b => <tr key={b.start}><td>{bucketLabel(b, monthly)}</td><td className="num">{b.sales}</td><td className="num">{money.format(b.gross)}</td><td className="num">{minus(b.returns)}</td><td className="num">{minus(b.voids)}</td><td className="num">{money.format(b.net)}</td></tr>)}</tbody></table></div></details>
  </div>;
}

function nice(v:number) { if (v <= 0) return 0; const p = 10 ** Math.floor(Math.log10(v)); const n = v / p; return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p; }
function axis(v:number) {
  const a = Math.abs(v), sign = v < 0 ? "−" : "";
  return a >= 1_000_000 ? `${sign}₡${number.format(a / 1_000_000)} M` : a >= 1000 ? `${sign}₡${number.format(a / 1000)} mil` : `${sign}₡${number.format(a)}`;
}

function Meter({ value, max }:{ value:number; max:number }) {
  return <div className="reports-meter" aria-hidden="true"><i style={{ width: `${max > 0 ? Math.max(0, value) / max * 100 : 0}%` }}/></div>;
}

function ProductsView({ data, search, setSearch, category, setCategory, sort, setSort, filtered }:{ data:Products; search:string; setSearch:(v:string)=>void;
  category:string; setCategory:(v:string)=>void; sort:string; setSort:(v:string)=>void; filtered:boolean }) {
  if (!data.gross && !data.returned && !filtered) return <Empty text="No se vendieron productos. Elige otro período o sucursal."/>;
  const maxCat = data.categories[0]?.amount ?? 0, maxProduct = Math.max(0, ...data.products.map(p => p.amount));
  return <>
    {data.categories.length > 0 && <section className="reports-panel"><div className="reports-panel-head"><h2>Por categoría</h2><small>Ventas brutas del período</small></div>
      <table className="reports-table reports-wide"><thead><tr><th>Categoría</th><th className="num">Productos vendidos</th><th className="num">Monto</th><th className="num">% del total</th><th className="reports-meter-col"><span className="sr-only">Proporción</span></th></tr></thead>
        <tbody>{data.categories.map(c => <tr key={c.category}><td>{c.category}</td><td className="num">{c.products}</td><td className="num">{money.format(c.amount)}</td><td className="num">{percent.format(c.share)}</td><td><Meter value={c.amount} max={maxCat}/></td></tr>)}</tbody>
        <tfoot><tr><td>Total</td><td className="num">{data.categories.reduce((s, c) => s + c.products, 0)}</td><td className="num">{money.format(data.gross)}</td><td className="num">{data.gross ? percent.format(1) : "—"}</td><td/></tr></tfoot></table>
      <div className="reports-cards">{data.categories.map(c => <article key={c.category} className="reports-card"><div className="reports-card-head"><strong>{c.category}</strong><span className="num">{money.format(c.amount)}</span></div><Meter value={c.amount} max={maxCat}/><small>{c.products} productos · {percent.format(c.share)} del total</small></article>)}</div>
    </section>}
    <div className="reports-table-wrap">
      <div className="reports-tools">
        <label className="inventory-search"><Search size={17}/><input type="search" aria-label="Buscar producto" placeholder="Nombre, SKU o código" value={search} onChange={e => setSearch(e.target.value)}/></label>
        <select aria-label="Categoría" value={category} onChange={e => setCategory(e.target.value)}><option value="">Todas las categorías</option>{data.categoryOptions.map(c => <option key={c}>{c}</option>)}</select>
        <select aria-label="Ordenar por" value={sort} onChange={e => setSort(e.target.value)}><option value="AMOUNT">Mayor monto</option><option value="QUANTITY">Mayor cantidad</option><option value="AMOUNT_ASC">Menor monto</option></select>
      </div>
      {data.products.length === 0 ? <div className="reports-inline-empty" role="status">No hay productos que coincidan con la búsqueda o la categoría.</div> : <>
        <table className="reports-table reports-wide"><thead><tr><th>#</th><th>Producto</th><th className="num">Cantidad</th><th className="num">Monto bruto</th><th className="num">Devuelto</th><th className="num">% del total</th></tr></thead>
          <tbody>{data.products.map((p, i) => <tr key={p.itemId}><td>{i + 1}</td><td><strong>{label(p)}</strong><small>SKU {p.sku} · {p.category}</small></td><td className="num">{number.format(p.quantity)} {p.unit}</td>
            <td className="num">{money.format(p.amount)}</td><td className="num">{minus(p.returned)}</td><td className="num">{percent.format(p.share)}</td></tr>)}</tbody>
          {!filtered && <tfoot><tr><td/><td>Total del período</td><td className="num">—</td><td className="num">{money.format(data.gross)}</td><td className="num">{minus(data.returned)}</td><td className="num">{data.gross ? percent.format(1) : "—"}</td></tr></tfoot>}</table>
        <div className="reports-cards">{data.products.map((p, i) => <article key={p.itemId} className="reports-card"><div className="reports-card-head"><div><strong>{i + 1}. {label(p)}</strong><small>SKU {p.sku}</small></div><span className="num">{money.format(p.amount)}</span></div>
          <Meter value={p.amount} max={maxProduct}/><small>{number.format(p.quantity)} {p.unit} · {percent.format(p.share)} del total{p.returned ? ` · devuelto ${money.format(p.returned)}` : ""}</small></article>)}</div></>}
    </div>
    <p className="reports-note">Cantidad en la unidad de venta de cada producto; por eso no se suma entre productos. Los montos son lo cobrado al cliente, sin costos ni ganancia (Stockly no registra costos).</p>
  </>;
}

function PaymentsView({ data }:{ data:Payments }) {
  if (!data.total.collected && !data.total.refunded && !data.unpaidSales) return <Empty text="No se registraron cobros ni reintegros. Elige otro período o sucursal."/>;
  const max = Math.max(0, ...data.methods.map(m => m.net));
  return <>
    <div className="reports-table-wrap">
      <table className="reports-table reports-wide"><thead><tr><th>Medio de pago</th><th className="num">Pagos</th><th className="num">Cobrado</th><th className="num">Reintegrado</th><th className="num">Neto</th><th className="reports-meter-col"><span className="sr-only">Proporción</span></th></tr></thead>
        <tbody>{data.methods.map(m => <tr key={m.method}><td><strong>{m.label}</strong></td><td className="num">{m.payments}</td><td className="num">{money.format(m.collected)}</td><td className="num">{minus(m.refunded)}</td><td className="num"><strong>{money.format(m.net)}</strong></td><td><Meter value={m.net} max={max}/></td></tr>)}</tbody>
        <tfoot><tr><td>Total</td><td className="num">{data.total.payments}</td><td className="num">{money.format(data.total.collected)}</td><td className="num">{minus(data.total.refunded)}</td><td className="num">{money.format(data.total.net)}</td><td/></tr></tfoot></table>
      <div className="reports-cards">{data.methods.map(m => <article key={m.method} className="reports-card"><div className="reports-card-head"><strong>{m.label}</strong><strong className="num">{money.format(m.net)}</strong></div><Meter value={m.net} max={max}/>
        <dl><div><dt>Pagos</dt><dd>{m.payments}</dd></div><div><dt>Cobrado</dt><dd>{money.format(m.collected)}</dd></div><div><dt>Reintegrado</dt><dd>{minus(m.refunded)}</dd></div></dl></article>)}
        <article className="reports-card"><div className="reports-card-head"><strong>Total neto</strong><strong className="num">{money.format(data.total.net)}</strong></div></article></div>
      {data.unpaidSales > 0 && <div className="reports-unpaid"><span><strong>Sin cobro registrado</strong><small>Ventas registradas antes de los cobros (STK-012)</small></span><span className="num">{data.unpaidSales} · {money.format(data.unpaidAmount)}</span></div>}
    </div>
    <p className="reports-note">Neto = cobrado − reintegrado. Es el mismo cálculo del Resumen de cobros: los reintegros de anulaciones y devoluciones cuentan en su fecha. Ventas netas del período: {money.format(data.netSales)}{data.netSales !== data.total.net ? "; la diferencia viene de ventas sin cobro registrado o de anulaciones del mismo día cuyo cobro y reintegro sí aparecen aquí" : ""}.</p>
  </>;
}

function SellersView({ data }:{ data:Sellers }) {
  if (data.rows.length === 0) return <Empty text="Nadie registró ventas en este período. Elige otro período o sucursal."/>;
  const t = data.total;
  return <>
    <div className="reports-table-wrap">
      <table className="reports-table reports-wide"><thead><tr><th>Vendedor</th><th className="num">Ventas</th><th className="num">Brutas</th><th className="num">Devoluciones</th><th className="num">Anulaciones</th><th className="num">Netas</th><th className="num">Venta promedio</th></tr></thead>
        <tbody>{data.rows.map(s => <tr key={s.sellerId}><td><strong>{s.name}</strong></td><td className="num">{s.sales}</td><td className="num">{money.format(s.gross)}</td><td className="num">{minus(s.returns)}</td><td className="num">{minus(s.voids)}</td><td className="num"><strong>{money.format(s.net)}</strong></td><td className="num">{wholeMoney.format(s.average)}</td></tr>)}</tbody>
        <tfoot><tr><td>Total</td><td className="num">{t.sales}</td><td className="num">{money.format(t.gross)}</td><td className="num">{minus(t.returns)}</td><td className="num">{minus(t.voids)}</td><td className="num">{money.format(t.net)}</td><td className="num">{wholeMoney.format(t.average)}</td></tr></tfoot></table>
      <div className="reports-cards">{data.rows.map(s => <article key={s.sellerId} className="reports-card"><div className="reports-card-head"><strong>{s.name}</strong><strong className="num">{money.format(s.net)}</strong></div>
        <dl><div><dt>Ventas</dt><dd>{s.sales}</dd></div><div><dt>Promedio</dt><dd>{wholeMoney.format(s.average)}</dd></div><div><dt>Devoluciones</dt><dd>{minus(s.returns + s.voids)}</dd></div></dl></article>)}</div>
    </div>
    <p className="reports-note">La venta se asigna a quien la registró. Cada devolución, y cada anulación hecha otro día, resta a quien hizo la venta original en la fecha en que se hizo.</p>
  </>;
}

function IdleView({ data, search, setSearch, onlyStock, setOnlyStock, all, filtered }:{ data:Idle; search:string; setSearch:(v:string)=>void;
  onlyStock:boolean; setOnlyStock:(v:boolean)=>void; all:boolean; filtered:boolean }) {
  const last = (v:string|null) => v ? crDate.format(new Date(v)) : "Nunca vendido";
  return <>
    <div className="reports-table-wrap">
      <div className="reports-tools">
        <label className="inventory-search"><Search size={17}/><input type="search" aria-label="Buscar producto" placeholder="Nombre, SKU o código" value={search} onChange={e => setSearch(e.target.value)}/></label>
        <label className="inventory-check reports-switch"><input type="checkbox" checked={onlyStock} onChange={e => setOnlyStock(e.target.checked)}/><span>Solo con existencias</span></label>
      </div>
      {data.rows.length === 0 ? <div className="reports-inline-empty" role="status">{filtered ? "No hay productos que coincidan con la búsqueda." : "Todos los productos se vendieron al menos una vez en el período."}</div> : <>
        <table className="reports-table reports-wide"><thead><tr><th>Producto</th>{all && <th>Sucursal</th>}<th>Categoría</th><th className="num">Disponible hoy</th><th>Última venta</th></tr></thead>
          <tbody>{data.rows.map(r => <tr key={r.branchId + r.productId + r.sku}><td><strong>{label(r)}</strong><small>SKU {r.sku}</small></td>{all && <td>{r.branchName}</td>}<td>{r.category}</td><td className="num">{number.format(r.available)} {r.unit}</td><td>{last(r.lastSale)}</td></tr>)}</tbody></table>
        <div className="reports-cards">{data.rows.map(r => <article key={r.branchId + r.productId + r.sku} className="reports-card"><div className="reports-card-head"><div><strong>{label(r)}</strong><small>SKU {r.sku} · {r.category}{all ? ` · ${r.branchName}` : ""}</small></div></div>
          <dl><div><dt>Disponible</dt><dd>{number.format(r.available)} {r.unit}</dd></div><div className="wide"><dt>Última venta</dt><dd>{last(r.lastSale)}</dd></div></dl></article>)}</div></>}
    </div>
    <p className="reports-note">Productos activos de la sucursal que no se vendieron en el período elegido. Ayuda a detectar mercadería quieta; no dice cuánto dinero representa porque Stockly no registra costos.</p>
  </>;
}

function PrintSheet({ data, who, place, period, onBack }:{ data:{ summary:Summary; payments:Payments; products:Products }; who:string; place:string; period:string; onBack:()=>void }) {
  const k = data.summary.current, monthly = data.summary.context.grouping === "MONTH";
  return <section className="reports-page">
    <div className="page-heading"><div><p className="eyebrow">OPERACIÓN COMERCIAL / REPORTES</p><h1>Vista para imprimir</h1><p className="subtitle">Se abre el cuadro de impresión del navegador; también permite guardar como PDF. Usa el período y la sucursal elegidos.</p></div>
      <div className="reports-actions"><button type="button" className="button secondary" onClick={onBack}>Volver a reportes</button><button type="button" className="button primary" onClick={() => window.print()}><Printer size={16}/> Imprimir</button></div></div>
    <article className="reports-sheet">
      <header><div><strong>{data.summary.context.companyName}</strong><h2>Resumen de ventas</h2><p>{place} · {period}</p></div><p>{crTime.format(new Date())}<br/>Generado por {who}</p></header>
      <table><tbody>
        <tr><td>Ventas brutas</td><td className="num">{money.format(k.gross)}</td><td>Ventas realizadas</td><td className="num">{k.sales}</td></tr>
        <tr><td>Devoluciones</td><td className="num">{minus(k.returns)}</td><td>Venta promedio</td><td className="num">{wholeMoney.format(k.average)}</td></tr>
        <tr><td>Anulaciones de ventas anteriores</td><td className="num">{minus(k.laterVoids)}</td><td>Anuladas el mismo día (no suman)</td><td className="num">{k.sameDayVoids} · {money.format(k.sameDayVoidAmount)}</td></tr>
        <tr><td><strong>Ventas netas</strong></td><td className="num"><strong>{money.format(k.net)}</strong></td><td/><td/></tr></tbody></table>
      <h3>Cobros por medio de pago</h3>
      <table><thead><tr><th>Medio</th><th className="num">Cobrado</th><th className="num">Reintegrado</th><th className="num">Neto</th></tr></thead>
        <tbody>{data.payments.methods.map(m => <tr key={m.method}><td>{m.label}</td><td className="num">{money.format(m.collected)}</td><td className="num">{minus(m.refunded)}</td><td className="num">{money.format(m.net)}</td></tr>)}
          <tr><td><strong>Total</strong></td><td className="num">{money.format(data.payments.total.collected)}</td><td className="num">{minus(data.payments.total.refunded)}</td><td className="num"><strong>{money.format(data.payments.total.net)}</strong></td></tr>
          {data.payments.unpaidSales > 0 && <tr><td>Sin cobro registrado</td><td className="num" colSpan={3}>{data.payments.unpaidSales} ventas · {money.format(data.payments.unpaidAmount)}</td></tr>}</tbody></table>
      <h3>Ventas por {monthly ? "mes" : "día"}</h3>
      <table><thead><tr><th>{monthly ? "Mes" : "Día"}</th><th className="num">Ventas</th><th className="num">Netas</th></tr></thead>
        <tbody>{data.summary.series.map(b => <tr key={b.start}><td>{bucketLabel(b, monthly)}</td><td className="num">{b.sales}</td><td className="num">{money.format(b.net)}</td></tr>)}</tbody></table>
      <h3>Productos más vendidos</h3>
      <table><thead><tr><th>Producto</th><th className="num">Cantidad</th><th className="num">Monto</th></tr></thead>
        <tbody>{data.products.products.slice(0, 10).map(p => <tr key={p.itemId}><td>{label(p)}</td><td className="num">{number.format(p.quantity)} {p.unit}</td><td className="num">{money.format(p.amount)}</td></tr>)}</tbody></table>
      <footer>Las devoluciones y las anulaciones de otros días restan en su fecha; las anuladas el mismo día no suman. Montos cobrados al cliente; no incluye costos ni ganancia.</footer>
    </article>
  </section>;
}
