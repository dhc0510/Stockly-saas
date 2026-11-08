import { useEffect, useState } from "react";
import { Link, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { Plus, Trash2 } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { MovementBranch, useMovementBranch } from "../inventory/MovementBranch";
import { amount, failureMessage, receiptDate, type Article } from "../receipts/receiptApi";
import { ApiError, request } from "../../lib/http";
import "../receipts/receipts.css";
import "./sales.css";

const api = "/api/sales";
const path = "/sales";
const currency = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC" });
const money = (value: number) => currency.format(value);
type Product = { article: Article; price: number | null };
type Line = Product & { quantity: string };
type Draft = { branchId?: string; customer: string; lines: Line[]; requestKey?: string };
type Row = { id: string; folio: string; date: string; branchName: string; customer: string | null; total: number; voided: boolean };
type Sale = {
  sale: { branchName: string; customer: string | null; total: number };
  movement: { folio: string; date: string; actor: string; voidId: string | null; voidReason: string | null; voidFolio: string | null };
  lines: { itemId: string; name: string; sku: string; unit: string; quantity: number; unitPrice: number; amount: number }[];
};
const empty = (): Draft => ({ customer: "", lines: [] });
function load(key: string): Draft {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? "null");
    return value && Array.isArray(value.lines) && typeof value.customer === "string" ? value : empty();
  } catch { return empty(); }
}

function Heading({ title, children }: { title: string; children?: React.ReactNode }) {
  return <header className="catalog-heading"><div><p className="catalog-eyebrow">Operación comercial / Ventas</p><h1>{title}</h1></div>{children}</header>;
}

export function SalesPage() {
  const { user } = useAuth();
  if (!user) return null;
  const storage = ["stockly", "sale", user.companyId, user.id].join(":");
  const level = user.modules.find(module => module.moduleKey === "SALES")?.accessLevel;
  const admin = user.role === "COMPANY_ADMIN";
  const canWrite = admin || level === "OPERATE" || level === "ADMIN";
  const canVoid = admin || user.specialPermissions.includes("VOID_SALES");
  return <div className="catalog-page sales-page"><Routes>
    <Route index element={<List storage={storage} canWrite={canWrite} />} />
    {canWrite && <Route path="new" element={<Editor storage={storage} />} />}
    <Route path=":folio" element={<Document canVoid={canVoid} />} />
  </Routes></div>;
}

function List({ storage, canWrite }: { storage: string; canWrite: boolean }) {
  const draft = load(storage);
  const [hasDraft, setHasDraft] = useState(!!(draft.lines.length || draft.customer.trim()));
  const [discard, setDiscard] = useState(false);
  const [search, setSearch] = useState("");
  const [branchId, setBranchId] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<{ entries: Row[]; hasMore: boolean }>();
  const [error, setError] = useState("");
  const { branches } = useMovementBranch(api);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setResult(undefined);
    const params = new URLSearchParams({ search, branchId, status, page: String(page) });
    request<{ entries: Row[]; hasMore: boolean }>(api + "?" + params, { signal: controller.signal }).then(setResult)
      .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [search, branchId, status, page]);

  return <>
    <Heading title="Ventas">{canWrite && <Link className="catalog-primary" to={path + "/new"}><Plus size={17} /> Nueva venta</Link>}</Heading>
    {canWrite && hasDraft && <div className="notice">
      <p>Tienes una venta sin confirmar.</p>
      <Link to={path + "/new"}>Continuar</Link>
      {!draft.requestKey && <button className="button secondary" type="button" onClick={() => setDiscard(true)}>Descartar</button>}
      {discard && <div><p>¿Descartar el borrador de este navegador?</p><button autoFocus type="button" onClick={() => setDiscard(false)}>Volver</button>
        <button type="button" onClick={() => {
          try { localStorage.removeItem(storage); setHasDraft(false); setDiscard(false); }
          catch (failure) { setError(failureMessage(failure)); }
        }}>Descartar borrador</button></div>}
    </div>}
    <section className="catalog-panel receipt-panel">
      <div className="sales-filters">
        <label>Buscar<input type="search" placeholder="Folio o cliente" value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} /></label>
        <label>Sucursal<select value={branchId} onChange={event => { setBranchId(event.target.value); setPage(0); }}>
          <option value="">Todas</option>{branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
        </select></label>
        <label>Estado<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">Todos</option><option value="confirmed">Confirmadas</option><option value="voided">Anuladas</option>
        </select></label>
      </div>
      {error ? <p role="alert">{error}</p> : !result ? <p role="status">Cargando ventas…</p> :
        !result.entries.length ? <p>No hay ventas para esta consulta.</p> : <ul className="sales-list">
          {result.entries.map(sale => <li key={sale.id}><Link to={path + "/" + sale.folio}>
            <strong>{sale.folio}</strong><time className="sales-date">{receiptDate(sale.date)}</time>
            <span>{sale.branchName} · {sale.customer || "Público general"}</span>
            <strong>{money(sale.total)}</strong><span>{sale.voided ? "Anulada" : "Confirmada"}</span>
          </Link></li>)}
        </ul>}
      {(page > 0 || result?.hasMore) && <div className="sales-actions">
        <button type="button" disabled={!page} onClick={() => setPage(page - 1)}>Anterior</button>
        <button type="button" disabled={!result?.hasMore} onClick={() => setPage(page + 1)}>Siguiente</button>
      </div>}
    </section>
  </>;
}

function quantity(line: Line) { return line.quantity.trim().replace(",", "."); }
function valid(line: Line) {
  const value = quantity(line);
  return (line.article.unit === "unidad" ? /^\d+$/ : /^\d+(\.\d{1,3})?$/).test(value) && Number(value) > 0;
}
function lineCents(line: Line) {
  if (!valid(line) || line.price === null) return 0n;
  const [whole, decimal = ""] = quantity(line).split(".");
  const thousandths = BigInt(whole) * 1000n + BigInt(decimal.padEnd(3, "0"));
  return (thousandths * BigInt(Math.round(line.price * 100)) + 500n) / 1000n;
}

function Editor({ storage }: { storage: string }) {
  const navigate = useNavigate();
  const [draft, setDraft] = useState(() => load(storage));
  const [review, setReview] = useState(!!draft.requestKey);
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<Product[]>([]);
  const [error, setError] = useState("");
  const [issues, setIssues] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [discard, setDiscard] = useState(false);
  const branch = useMovementBranch(api, draft.branchId);
  const branchId = branch.selected?.id;
  const pending = !!draft.requestKey;
  const total = draft.lines.reduce((sum, line) => sum + lineCents(line), 0n);
  useEffect(() => {
    const controller = new AbortController();
    setResults([]);
    if (!branchId || branch.problem || !search.trim() || review) return;
    const timer = window.setTimeout(() => {
      request<Product[]>(api + "/items?" + new URLSearchParams({ branchId, search: search.trim() }), { signal: controller.signal })
        .then(setResults).catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    }, 180);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [branchId, branch.problem, search, review]);

  function persist(next: Draft) {
    try { localStorage.setItem(storage, JSON.stringify(next)); }
    catch { setError("El navegador no pudo guardar el borrador. Libera espacio y vuelve a intentar."); return false; }
    setDraft(next);
    return true;
  }
  function update(next: Partial<Draft>) {
    setError("");
    setIssues({});
    persist({ ...draft, ...next });
  }
  function startReview() {
    const invalid = Object.fromEntries(draft.lines.flatMap(line => !valid(line)
      ? [[line.article.id, line.article.unit === "unidad" ? "Ingresa una cantidad entera positiva." : "Ingresa una cantidad positiva de hasta tres decimales."]]
      : line.price === null ? [[line.article.id, "Configura el precio en el catálogo."]] : []));
    setIssues(invalid);
    if (Object.keys(invalid).length) { setError("Revisa los artículos marcados."); return; }
    setReview(true);
  }
  async function confirm() {
    if (saving || (!draft.branchId && !branchId)) return;
    const attempt = { ...draft, branchId: draft.branchId ?? branchId, requestKey: draft.requestKey ?? crypto.randomUUID() };
    if (!persist(attempt)) return;
    setSaving(true);
    setError("");
    try {
      const sale = await request<Sale>(api, { method: "POST", body: JSON.stringify({
        requestKey: attempt.requestKey, branchId: attempt.branchId, customer: attempt.customer,
        lines: attempt.lines.map(line => ({ itemId: line.article.id, quantity: quantity(line), expectedPrice: line.price })),
      }) });
      localStorage.removeItem(storage);
      navigate(path + "/" + sale.movement.folio, { replace: true });
    } catch (failure) {
      setError(failureMessage(failure));
      if (failure instanceof ApiError && failure.code === "SALE_PRICE_CHANGED") {
        const notes: Record<string, string> = {};
        const lines = draft.lines.map(line => {
          const price = failure.fieldErrors[line.article.id];
          if (price === undefined) return line;
          notes[line.article.id] = "Precio anterior " + (line.price === null ? "sin precio" : money(line.price)) + " · Nuevo " + money(Number(price));
          return { ...line, price: Number(price) };
        });
        persist({ ...attempt, lines, requestKey: undefined });
        setIssues(notes);
      } else if (failure instanceof ApiError && failure.code === "SALE_LINE_ERROR") {
        persist({ ...attempt, requestKey: undefined });
        setIssues(failure.fieldErrors);
        setReview(false);
      } else if (failure instanceof ApiError && ["SALE_INVALID", "SALE_NOT_READY"].includes(failure.code)) {
        persist({ ...attempt, requestKey: undefined });
      }
    } finally { setSaving(false); }
  }

  return <>
    <Heading title={review ? "Revisar venta" : "Nueva venta"}><Link to={path}>Volver a ventas</Link></Heading>
    {error && <p className="notice error" role="alert">{error}</p>}
    <section className="catalog-panel receipt-panel">
      {review ? <><h2>{branch.selected?.name ?? "Sucursal seleccionada"}</h2><p>{draft.customer || "Público general"}</p></> :
        <div className="sales-filters">
          {branch.legacy ? <p role="status">Crea y configura una sucursal antes de registrar ventas.</p> :
            <MovementBranch state={branch} locked={draft.lines.length > 0} onChange={id => { setSearch(""); update({ branchId: id }); }} />}
          <label>Cliente opcional<input value={draft.customer} maxLength={150} onChange={event => update({ customer: event.target.value })} /></label>
        </div>}
    </section>
    <section className="catalog-panel receipt-panel">
      <h2>Artículos</h2>
      {!review && <>
        <label>Buscar por nombre, SKU o código de barras<input type="search" value={search} disabled={!branchId || !!branch.problem}
          onChange={event => setSearch(event.target.value)} /></label>
        {!!results.length && <ul className="sales-results">{results.map(product => <li key={product.article.id}>
          <button type="button" className="button secondary" onClick={() => {
            if (draft.lines.some(line => line.article.id === product.article.id)) return;
            update({ branchId, lines: [...draft.lines, { ...product, quantity: "" }] });
            setSearch("");
          }} disabled={draft.lines.some(line => line.article.id === product.article.id)}>
            {product.article.name} · {product.article.sku} · {amount(product.article.balance, product.article.unit)} disponibles · {product.price === null ? "Sin precio" : money(product.price)}
          </button></li>)}</ul>}
      </>}
      <ul className="sales-lines">
        {draft.lines.map(line => <li key={line.article.id}>
          <div><strong>{line.article.name}</strong><small>{line.article.sku} · {line.price === null ? "Sin precio" : money(line.price)}</small></div>
          {review ? <span>{amount(Number(quantity(line)), line.article.unit)}</span> :
            <label>Cantidad · {line.article.unit}<input inputMode={line.article.unit === "unidad" ? "numeric" : "decimal"}
              value={line.quantity} maxLength={24} aria-invalid={!!issues[line.article.id]}
              onChange={event => update({ lines: draft.lines.map(other => other === line ? { ...line, quantity: event.target.value } : other) })} /></label>}
          <strong>{money(Number(lineCents(line)) / 100)}</strong>
          {!review && <button className="icon-button" type="button" aria-label={"Quitar " + line.article.name}
            onClick={() => update({ lines: draft.lines.filter(other => other !== line) })}><Trash2 size={16} /></button>}
          {issues[line.article.id] && <p className="sales-line-note">{issues[line.article.id]}</p>}
        </li>)}
      </ul>
      <p className="sales-total">Total <strong>{money(Number(total) / 100)}</strong></p>
      <div className="sales-actions">
        {review ? <>
          <button className="button secondary" type="button" disabled={saving || pending} onClick={() => { setReview(false); setIssues({}); setError(""); }}>Volver a editar</button>
          <button className="catalog-primary" type="button" disabled={saving || (!pending && (!branchId || !!branch.problem))}
            onClick={() => void confirm()}>{saving ? "Confirmando…" : pending ? "Consultar y reintentar" : "Confirmar venta"}</button>
        </> : <>
          <button className="button secondary" type="button" onClick={() => setDiscard(true)}>Descartar</button>
          <button className="catalog-primary" type="button" disabled={!draft.lines.length || !branchId || !!branch.problem} onClick={startReview}>Revisar venta</button>
        </>}
      </div>
      {discard && <div><p>¿Descartar esta venta sin confirmar?</p><div className="sales-actions">
        <button className="button secondary" type="button" autoFocus onClick={() => setDiscard(false)}>Conservar</button>
        <button className="button danger" type="button" onClick={() => {
          try { localStorage.removeItem(storage); navigate(path); }
          catch (failure) { setError(failureMessage(failure)); }
        }}>Descartar borrador</button>
      </div></div>}
    </section>
  </>;
}

function Document({ canVoid }: { canVoid: boolean }) {
  const { folio } = useParams();
  const [sale, setSale] = useState<Sale>();
  const [voiding, setVoiding] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const url = api + "/" + encodeURIComponent(folio ?? "");
  useEffect(() => {
    const controller = new AbortController();
    setSale(undefined);
    setError("");
    request<Sale>(url, { signal: controller.signal }).then(setSale)
      .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [url]);

  return <>
    <Heading title={sale ? "Venta " + sale.movement.folio : "Comprobante de venta"}><Link to={path}>Volver a ventas</Link></Heading>
    {error && <p className="notice error" role="alert">{error}</p>}
    {!sale ? !error && <p role="status">Cargando comprobante…</p> : <section className="catalog-panel receipt-panel">
      <h2>Detalle de la venta <small>{sale.movement.voidId ? <a href="#anulacion">Anulada</a> : "Confirmada"}</small></h2>
      <dl className="sales-details">
        <div><dt>Sucursal</dt><dd>{sale.sale.branchName}</dd></div>
        <div><dt>Fecha y hora</dt><dd><time className="sales-date">{receiptDate(sale.movement.date)}</time></dd></div>
        <div><dt>Responsable</dt><dd>{sale.movement.actor}</dd></div>
        {sale.sale.customer && <div><dt>Cliente</dt><dd>{sale.sale.customer}</dd></div>}
      </dl>
      <ul className="sales-lines">{sale.lines.map(line => <li key={line.itemId}>
        <div><strong>{line.name}</strong><small>{line.sku}</small></div>
        <span>{amount(line.quantity, line.unit)} · {money(line.unitPrice)} por {line.unit}</span><strong>{money(line.amount)}</strong>
      </li>)}</ul>
      <p className="sales-total">Total <strong>{money(sale.sale.total)}</strong></p>
      {sale.movement.voidId ? <div className="notice" id="anulacion"><strong>Anulación {sale.movement.voidFolio}</strong><p>{sale.movement.voidReason}</p></div> :
        canVoid && (voiding ? <form onSubmit={async event => {
          event.preventDefault();
          if (!reason.trim()) { setError("Escribe el motivo de la anulación."); return; }
          setError(""); setBusy(true);
          try {
            setSale(await request<Sale>(url + "/void", { method: "POST", body: JSON.stringify({ reason }) }));
            setVoiding(false);
          } catch (failure) { setError(failureMessage(failure)); }
          finally { setBusy(false); }
        }}>
          <label>Motivo de anulación<textarea autoFocus maxLength={150} value={reason} onChange={event => setReason(event.target.value)} /></label>
          <div className="sales-actions">
            <button className="button secondary" type="button" disabled={busy} onClick={() => setVoiding(false)}>Cancelar</button>
            <button className="button danger" type="submit" disabled={busy}>Confirmar anulación</button>
          </div>
        </form> : <button type="button" className="button danger" onClick={() => setVoiding(true)}>Anular venta</button>)}
    </section>}
  </>;
}
