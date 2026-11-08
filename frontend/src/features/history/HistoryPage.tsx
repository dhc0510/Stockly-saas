import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Search } from "lucide-react";
import type { SessionUser } from "../auth/api";
import { useAuth } from "../auth/AuthContext";
import { Difference } from "../movements/MovementEditor";
import { amount, failureMessage, receiptDate } from "../receipts/receiptApi";
import "../receipts/receipts.css";
import "../movements/movements.css";
import "./history.css";
import { costaRicaDay, historyApi, kindLabels, shortDay, type ActorOption, type HistoryRow, type ItemOption, type ItemSummary, type MovementDocument, type MovementKind } from "./historyApi";

export function historyAccess(user: SessionUser) {
  const level = user.modules.find(module => module.moduleKey === "INVENTORY_HISTORY")?.accessLevel;
  return user.role === "COMPANY_ADMIN" || (!!level && level !== "NONE");
}

const base = "/inventory/history";
const round = (value: number) => Math.round(value * 1000) / 1000;
const DAY = 86_400_000;
type Period = "30" | "day" | "range";
const initial = () => ({ search: "", item: undefined as ItemOption | undefined, itemText: "", type: "", actorId: "", period: "30" as Period, day: costaRicaDay(Date.now()), from: costaRicaDay(Date.now() - 30 * DAY), to: costaRicaDay(Date.now()) });

export function HistoryPage() {
  const { "*": segment = "" } = useParams();
  const [section, id] = segment.split("/");
  if (section === "items" && id) return <ItemHistory key={id} id={id} />;
  if (section === "movements" && id) return <MovementView key={id} id={id} />;
  return <GeneralHistory />;
}

function Heading({ title, text, back }: { title: string; text: string; back?: boolean }) {
  const navigate = useNavigate();
  return <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Historial</p><h1>{title}</h1><p className="subtitle">{text}</p></div>
    {back && <button onClick={() => navigate(base)}>Volver al historial</button>}</div>;
}

function KindBadges({ row }: { row: { kind: MovementKind; voidFolio: string | null } }) {
  return <span className="history-badges"><span className="catalog-badge">{kindLabels[row.kind]}</span>{row.voidFolio && <span className="catalog-badge history-voided">Anulado</span>}</span>;
}

function Rows({ rows, showItem }: { rows: HistoryRow[]; showItem: boolean }) {
  return <table className={"receipt-table receipt-list history-table" + (showItem ? "" : " history-no-item")}><thead><tr><th>Fecha</th><th>Folio</th>{showItem && <th>Artículo</th>}<th>Motivo</th><th>Responsable</th><th>Cambio</th><th>Saldo después</th></tr></thead>
    <tbody>{rows.map(row => <tr key={row.movementId + row.itemId}>
      <td className="receipt-date history-cell-date"><time dateTime={row.date}>{receiptDate(row.date)}</time></td>
      <td className="history-cell-folio"><Link className="receipt-link history-folio" to={base + "/movements/" + row.movementId}>{row.folio}</Link><KindBadges row={row} /></td>
      {showItem && <td className="history-cell-item"><Link className="receipt-link" to={base + "/items/" + row.itemId}>{row.itemName}</Link>
        <small>{row.sku}{!row.itemActive && <> · <span className="catalog-badge history-inactive">Inactivo</span></>}</small></td>}
      <td className="history-cell-reason">{row.reason}{row.reference && <small>{row.reference}</small>}{row.originalFolio && <small>Anula {row.originalFolio}</small>}</td>
      <td className="history-cell-actor"><span className="receipt-mobile-label">Registrado por</span> {row.actor}</td>
      <td className="history-number history-cell-change"><Difference value={row.quantity} unit={row.unit} /></td>
      <td className="history-number history-cell-balance"><span className="receipt-mobile-label">Saldo</span> <strong>{amount(row.balanceAfter, row.unit)}</strong></td>
    </tr>)}</tbody></table>;
}

function Pager({ page, more, set }: { page: number; more: boolean; set: (page: number) => void }) {
  if (page === 0 && !more) return null;
  return <div className="catalog-pagination"><button disabled={page === 0} onClick={() => set(page - 1)}>Anterior</button><span>Página {page + 1}</span><button disabled={!more} onClick={() => set(page + 1)}>Siguiente</button></div>;
}

function GeneralHistory() {
  const [filters, setFilters] = useState(initial);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<HistoryRow[]>([]);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  const [actors, setActors] = useState<ActorOption[]>([]);
  const [suggestions, setSuggestions] = useState<ItemOption[]>();
  const [showSuggestions, setShowSuggestions] = useState(false);
  const results = useRef<HTMLDivElement>(null);
  const today = costaRicaDay(Date.now());

  const range = useMemo(() => filters.period === "30" ? { from: costaRicaDay(Date.now() - 30 * DAY), to: today }
    : filters.period === "day" ? { from: filters.day, to: filters.day } : { from: filters.from, to: filters.to }, [filters.period, filters.day, filters.from, filters.to, today]);
  const periodError = filters.period === "day" && !filters.day ? "Elige una fecha."
    : (range.from > today || range.to > today) ? "No se aceptan fechas futuras."
    : range.from && range.to && range.from > range.to ? "La fecha inicial no puede ser posterior a la final." : "";
  const periodText = filters.period === "30" ? "Últimos 30 días" : filters.period === "day" ? (filters.day ? "El " + shortDay(filters.day) : "Elige una fecha")
    : "Del " + (filters.from ? shortDay(filters.from) : "inicio") + " al " + (filters.to ? shortDay(filters.to) : "hoy");
  const filtered = !!(filters.search.trim() || filters.item || filters.type || filters.actorId || filters.period !== "30");

  function change(patch: Partial<ReturnType<typeof initial>>) { setFilters(current => ({ ...current, ...patch })); setPage(0); }

  useEffect(() => {
    const controller = new AbortController();
    historyApi.actors(controller.signal).then(setActors).catch(() => { /* El filtro queda solo con «Todos». */ });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (periodError) { setRows([]); setMore(false); setLoading(false); setError(""); return; }
    const controller = new AbortController();
    setLoading(true); setError("");
    const timer = window.setTimeout(() => {
      historyApi.list({ search: filters.search, itemId: filters.item?.id, type: filters.type, actorId: filters.actorId, from: range.from, to: range.to, page }, controller.signal)
        .then(result => { if (!controller.signal.aborted) { setRows(result.rows); setMore(result.hasMore); } })
        .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, 150);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [filters.search, filters.item, filters.type, filters.actorId, range, page, version, periodError]);

  const term = filters.itemText.trim();
  useEffect(() => {
    if (!showSuggestions || !term || filters.item) return;
    const controller = new AbortController();
    setSuggestions(undefined);
    const timer = window.setTimeout(() => {
      historyApi.items(term, controller.signal).then(items => { if (!controller.signal.aborted) setSuggestions(items); })
        .catch(() => { if (!controller.signal.aborted) setSuggestions([]); });
    }, 150);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [term, showSuggestions, filters.item]);

  return <section className="catalog-page receipts-page">
    <Heading title="Historial de movimientos" text="Consulta qué cambió cada existencia, cuándo y quién lo hizo. Es solo de lectura." />
    <div className="catalog-panel receipt-panel">
      <div className="history-filters">
        <label className="history-wide">Buscar<span className="catalog-search"><Search size={16} aria-hidden="true" />
          <input type="search" placeholder="Folio o referencia" maxLength={160} value={filters.search} onChange={event => change({ search: event.target.value })} /></span></label>
        <div className="history-wide receipt-item-search"><label>Artículo<span className="catalog-search"><Search size={16} aria-hidden="true" />
          <input type="search" autoComplete="off" maxLength={160} placeholder="Nombre, SKU o código de barras" value={filters.itemText}
            onFocus={() => { if (!filters.item) setShowSuggestions(true); }}
            onChange={event => { change({ itemText: event.target.value, item: undefined }); setShowSuggestions(true); }}
            onKeyDown={event => {
              if (event.key === "Escape") setShowSuggestions(false);
              if (event.key === "ArrowDown") { event.preventDefault(); results.current?.querySelector("button")?.focus(); }
            }} /></span></label>
          {showSuggestions && term && !filters.item && <div className="receipt-results history-results" ref={results}>
            {!suggestions ? <p role="status">Buscando…</p> : suggestions.length ? suggestions.map(item =>
              <button type="button" key={item.id} onClick={() => { change({ item, itemText: item.name + " · " + item.sku }); setShowSuggestions(false); }}>
                <strong>{item.name}</strong><small>{item.sku}{!item.active && " · Inactivo"}</small></button>) : <p>No encontramos artículos.</p>}
          </div>}
        </div>
        <label>Tipo<select value={filters.type} onChange={event => change({ type: event.target.value })}>
          <option value="">Todos</option>{Object.entries(kindLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label>Responsable<select value={filters.actorId} onChange={event => change({ actorId: event.target.value })}>
          <option value="">Todos</option>{actors.map(actor => <option key={actor.id} value={actor.id}>{actor.name}</option>)}</select></label>
      </div>
      <div className="history-period">
        <label>Periodo<select value={filters.period} onChange={event => change({ period: event.target.value as Period })}>
          <option value="30">Últimos 30 días</option><option value="day">Fecha específica</option><option value="range">Desde y hasta</option></select></label>
        {filters.period === "day" && <label>Fecha<input type="date" max={today} value={filters.day} onChange={event => change({ day: event.target.value })} /></label>}
        {filters.period === "range" && <><label>Desde<input type="date" max={today} value={filters.from} onChange={event => change({ from: event.target.value })} /></label>
          <label>Hasta<input type="date" max={today} value={filters.to} onChange={event => change({ to: event.target.value })} /></label></>}
        <button onClick={() => { setFilters(initial()); setPage(0); setShowSuggestions(false); }}>Limpiar filtros</button>
      </div>
      {periodError && <p className="notice error" role="alert">{periodError}</p>}
    </div>
    <div className="catalog-panel receipt-panel">
      <h2>Movimientos</h2>
      {error && <p className="notice error" role="alert">{error} <button onClick={() => setVersion(value => value + 1)}>Reintentar</button></p>}
      {periodError ? <p className="catalog-state">Corrige el periodo para ver resultados.</p>
        : loading ? <p className="catalog-state" role="status">Cargando movimientos…</p>
        : error ? null
        : <><p className="history-summary" role="status">{periodText}</p>
          {rows.length ? <Rows rows={rows} showItem /> : <p className="catalog-state">{filtered ? "Sin movimientos con estos filtros." : "Sin movimientos en los últimos 30 días."}</p>}
          <Pager page={page} more={more} set={setPage} /></>}
    </div>
  </section>;
}

function ItemHistory({ id }: { id: string }) {
  const [summary, setSummary] = useState<ItemSummary>();
  const [type, setType] = useState("");
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<HistoryRow[]>([]);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    Promise.all([historyApi.item(id, controller.signal), historyApi.list({ itemId: id, type, page }, controller.signal)])
      .then(([item, list]) => { if (!controller.signal.aborted) { setSummary(item); setRows(list.rows); setMore(list.hasMore); } })
      .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [id, type, page, version]);

  return <section className="catalog-page receipts-page">
    <Heading title={summary?.item.name ?? "Historia del artículo"} text="Toda la historia de este artículo, de lo más reciente a lo más antiguo." back />
    {error && <p className="notice error" role="alert">{error} <button onClick={() => setVersion(value => value + 1)}>Reintentar</button></p>}
    {summary && <div className="catalog-panel receipt-panel"><dl className="receipt-metadata history-facts">
      <div><dt>Saldo actual en ubicaciones autorizadas</dt><dd className="history-balance">{amount(summary.balance, summary.item.unit)}</dd></div>
      <div><dt>SKU</dt><dd>{summary.item.sku}</dd></div>
      <div><dt>Unidad</dt><dd>{summary.item.unit}</dd></div>
      <div><dt>Estado en catálogo</dt><dd>{summary.item.active ? "Activo" : <span className="catalog-badge history-inactive">Inactivo</span>}</dd></div>
    </dl></div>}
    <div className="catalog-panel receipt-panel">
      <h2>Cómo cambió el saldo</h2>
      <div className="history-type"><label>Tipo<select value={type} onChange={event => { setType(event.target.value); setPage(0); }}>
        <option value="">Todos</option>{Object.entries(kindLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label></div>
      {loading ? <p className="catalog-state" role="status">Cargando movimientos…</p>
        : !error && (rows.length ? <Rows rows={rows} showItem={false} /> : <p className="catalog-state">{type ? "Este artículo no tiene movimientos de este tipo." : "Este artículo aún no tiene movimientos."}</p>)}
      {!loading && !error && <Pager page={page} more={more} set={setPage} />}
    </div>
  </section>;
}

function MovementView({ id }: { id: string }) {
  const { user } = useAuth();
  const canSeeSales = user?.role === "COMPANY_ADMIN" ||
    user?.modules.some(module => module.moduleKey === "SALES" && module.accessLevel !== "NONE");
  const [document, setDocument] = useState<MovementDocument>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    historyApi.movement(id, controller.signal).then(result => { if (!controller.signal.aborted) setDocument(result); })
      .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [id]);
  if (!document) return <section className="catalog-page receipts-page"><Heading title="Comprobante" text="Consulta de solo lectura." back />
    {error ? <p className="notice error" role="alert">{error}</p> : <p role="status">Cargando comprobante…</p>}</section>;
  const { movement, lines } = document;
  const adjust = movement.kind === "ADJUST";
  return <section className="catalog-page receipts-page">
    <Heading title={(movement.kind === "VOID" ? "Movimiento inverso " : "Comprobante ") + movement.folio} text="Consulta de solo lectura. Para registrar o anular, usa el módulo correspondiente." back />
    <div className="catalog-panel receipt-panel">
      <div className="receipt-detail-title"><h2>Detalle del movimiento</h2><span className={movement.voidId ? "catalog-badge history-voided" : "catalog-badge active"}>{movement.voidId ? "Anulado" : "Confirmado"}</span></div>
      <dl className="receipt-metadata">
        <div><dt>Tipo</dt><dd>{kindLabels[movement.kind]}</dd></div>
        <div><dt>Ubicación</dt><dd>{movement.location}</dd></div>
        <div className="receipt-date"><dt>Fecha y hora</dt><dd><time dateTime={movement.date}>{receiptDate(movement.date)}</time></dd></div>
        <div><dt>Responsable</dt><dd>{movement.actor}</dd></div>
        <div><dt>Motivo</dt><dd>{movement.reason}</dd></div>
        <div><dt>Referencia</dt><dd>{movement.kind === "RETURN" && movement.reference && canSeeSales
          ? <Link to={"/sales/" + encodeURIComponent(movement.reference) + "/returns/" + encodeURIComponent(movement.folio)}>
            {movement.reference} · Ver devolución
          </Link>
          : movement.reference || "—"}</dd></div>
      </dl>
      {movement.voidId && <p className="receipt-note"><strong>Anulación:</strong> {movement.voidReason}{" "}<Link className="receipt-link" to={base + "/movements/" + movement.voidId}>Ver {movement.voidFolio}</Link></p>}
      {movement.originalId && <p className="receipt-note">Anula <Link className="receipt-link" to={base + "/movements/" + movement.originalId}>{movement.originalFolio}</Link></p>}
    </div>
    <div className="catalog-panel receipt-panel">
      <h2>Artículos</h2><p className="subtitle">Saldo registrado al confirmar; no cambia con movimientos posteriores.</p>
      <table className="receipt-table history-lines"><thead><tr><th>Artículo</th>{adjust ? <><th>Saldo antes</th><th>Contado</th><th>Diferencia</th></> : <><th>Cambio</th><th>Saldo después</th></>}</tr></thead>
        <tbody>{lines.map(line => <tr key={line.itemId}>
          <td><Link className="receipt-link" to={base + "/items/" + line.itemId}>{line.name}</Link><small>{line.sku}</small></td>
          {adjust ? <>
            <td><span className="receipt-mobile-label">Saldo antes</span>{amount(round(line.balanceAfter - line.quantity), line.unit)}</td>
            <td><span className="receipt-mobile-label">Contado</span><strong>{amount(line.balanceAfter, line.unit)}</strong></td>
            <td><span className="receipt-mobile-label">Diferencia</span><Difference value={line.quantity} unit={line.unit} /></td>
          </> : <>
            <td><span className="receipt-mobile-label">Cambio</span><Difference value={line.quantity} unit={line.unit} /></td>
            <td><span className="receipt-mobile-label">Saldo después</span><strong>{amount(line.balanceAfter, line.unit)}</strong></td>
          </>}
        </tr>)}</tbody></table>
    </div>
  </section>;
}
