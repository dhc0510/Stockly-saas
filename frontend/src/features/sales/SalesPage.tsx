import { useEffect, useRef, useState } from "react";
import { Link, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, ArrowRight, Building2, Check, ChevronRight, ClipboardList, FileText, Package, Plus, ReceiptText, Search, Trash2, UserRound, X } from "lucide-react";
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
type Draft = {
  branchId?: string;
  customer: string;
  customerPhone?: string;
  customerEmail?: string;
  lines: Line[];
  requestKey?: string;
};
type Row = { id: string; folio: string; date: string; branchName: string; customer: string | null; total: number; voided: boolean };
type Sale = {
  sale: { branchName: string; customer: string | null; customerPhone: string | null; customerEmail: string | null; total: number };
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

function Heading({ title, description, children }: { title: string; description: string; children?: React.ReactNode }) {
  return <header className="sales-heading">
    <div className="sales-heading-copy">
      <p className="sales-eyebrow"><ReceiptText size={15} aria-hidden="true" /> Operación comercial</p>
      <h1>{title}</h1>
      <p className="sales-description">{description}</p>
    </div>
    {children && <div className="sales-heading-action">{children}</div>}
  </header>;
}

function SaleStatus({ voided }: { voided: boolean }) {
  const Icon = voided ? X : Check;
  return <span className={voided ? "sales-status sales-status-void" : "sales-status"}>
    <Icon size={13} aria-hidden="true" />{voided ? "Anulada" : "Confirmada"}
  </span>;
}

function PanelHeading({ icon: Icon, title, description }: {
  icon: typeof ReceiptText;
  title: string;
  description: string;
}) {
  return <div className="sales-panel-heading">
    <span className="sales-icon" aria-hidden="true"><Icon size={19} /></span>
    <div><h2>{title}</h2><p>{description}</p></div>
  </div>;
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
  const [hasDraft, setHasDraft] = useState(!!(draft.lines.length || draft.customer.trim() || draft.customerPhone?.trim() || draft.customerEmail?.trim()));
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
    <Heading title="Ventas" description="Registra ventas y consulta sus comprobantes por sucursal.">
      {canWrite && <Link className="button primary sales-new-sale" to={path + "/new"}>
        <Plus size={18} aria-hidden="true" /> Nueva venta
      </Link>}
    </Heading>
    {canWrite && hasDraft && <aside className="sales-draft" aria-labelledby="sales-draft-title">
      <div className="sales-draft-copy">
        <span className="sales-icon" aria-hidden="true"><ClipboardList size={20} /></span>
        <div><h2 id="sales-draft-title">Venta sin confirmar</h2><p>Retoma el borrador que guardaste en este navegador.</p></div>
      </div>
      {discard ? <div className="sales-draft-confirm">
        <p>¿Descartar este borrador?</p>
        <div className="sales-draft-actions">
          <button className="button secondary" autoFocus type="button" onClick={() => setDiscard(false)}>Conservar</button>
          <button className="button danger" type="button" onClick={() => {
            try { localStorage.removeItem(storage); setHasDraft(false); setDiscard(false); }
            catch (failure) { setError(failureMessage(failure)); }
          }}>Descartar borrador</button>
        </div>
      </div> : <div className="sales-draft-actions">
        {!draft.requestKey && <button className="button sales-quiet" type="button" onClick={() => setDiscard(true)}>Descartar</button>}
        <Link className="button secondary" to={path + "/new"}>Continuar <ArrowRight size={16} aria-hidden="true" /></Link>
      </div>}
    </aside>}
    <section className="sales-panel sales-register" aria-label="Historial de ventas">
      <div className="sales-register-tools">
        <PanelHeading icon={ReceiptText} title="Historial de ventas" description="Busca por folio o cliente, o acota por sucursal y estado." />
        <div className="sales-filters">
          <label className="sales-search-filter">Buscar
            <span className="sales-search">
              <Search size={17} aria-hidden="true" />
              <input type="search" placeholder="Folio o cliente" value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} />
            </span>
          </label>
          <label>Sucursal<select value={branchId} onChange={event => { setBranchId(event.target.value); setPage(0); }}>
            <option value="">Todas</option>{branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
          </select></label>
          <label>Estado<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
            <option value="">Todos</option><option value="confirmed">Confirmadas</option><option value="voided">Anuladas</option>
          </select></label>
        </div>
      </div>
      {error ? <p className="sales-list-message" role="alert">{error}</p> :
        !result ? <p className="sales-list-message" role="status">Cargando ventas…</p> :
        !result.entries.length ? <div className="sales-empty">
          <span className="sales-icon" aria-hidden="true"><ReceiptText size={22} /></span>
          <h3>No hay ventas para esta consulta</h3>
          <p>{search || branchId || status ? "Prueba con otros filtros." : "Los comprobantes aparecerán aquí al confirmar una venta."}</p>
        </div> : <>
          <div className="sales-register-head sales-row-grid" aria-hidden="true">
            <span>Venta</span><span>Cliente</span><span>Sucursal</span><span>Fecha y hora</span>
            <span className="sales-amount">Total</span><span>Estado</span><span />
          </div>
          <ul className="sales-list" role="list">
            {result.entries.map(sale => <li key={sale.id}><Link className="sales-row sales-row-grid" to={path + "/" + sale.folio}>
              <span className="sales-folio"><span className="sales-folio-icon" aria-hidden="true"><FileText size={16} /></span><strong>{sale.folio}</strong></span>
              <span className="sales-row-customer">{sale.customer || "Público general"}</span>
              <span className="sales-row-branch"><Building2 size={14} aria-hidden="true" />{sale.branchName}</span>
              <time className="sales-date" dateTime={sale.date}>{receiptDate(sale.date)}</time>
              <strong className="sales-amount">{money(sale.total)}</strong>
              <SaleStatus voided={sale.voided} />
              <ChevronRight className="sales-row-arrow" size={17} aria-hidden="true" />
            </Link></li>)}
          </ul>
        </>}
      {(page > 0 || result?.hasMore) && <div className="sales-pagination">
        <button className="button secondary" type="button" disabled={!page} onClick={() => setPage(page - 1)}>Anterior</button>
        <button className="button secondary" type="button" disabled={!result?.hasMore} onClick={() => setPage(page + 1)}>Siguiente</button>
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
  const [emailInvalid, setEmailInvalid] = useState(false);
  const emailInput = useRef<HTMLInputElement>(null);
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
    const email = draft.customerEmail?.trim();
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailInvalid(true);
      emailInput.current?.focus();
      return;
    }
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
        customerPhone: attempt.customerPhone, customerEmail: attempt.customerEmail,
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
    <Heading title={review ? "Revisar venta" : "Nueva venta"} description={review ? "Verifica los artículos y el total antes de confirmar." : "Elige la sucursal y agrega los artículos de la venta."}>
      <Link className="button secondary sales-back" to={path}><ArrowLeft size={16} aria-hidden="true" /> Volver a ventas</Link>
    </Heading>
    {error && <p className="notice error" role="alert">{error}</p>}
    <section className="sales-panel sales-work-panel">
      {review ? <>
        <PanelHeading icon={UserRound} title={branch.selected?.name ?? "Sucursal seleccionada"} description="Datos de la venta" />
        <dl className="sales-details">
          <div><dt>Cliente</dt><dd>{draft.customer.trim() || "Público general"}</dd></div>
          {draft.customerPhone?.trim() && <div><dt>Teléfono</dt><dd>{draft.customerPhone.trim()}</dd></div>}
          {draft.customerEmail?.trim() && <div><dt>Correo electrónico</dt><dd>{draft.customerEmail.trim()}</dd></div>}
        </dl>
      </> : <>
        <PanelHeading icon={UserRound} title="Datos de la venta" description="Puedes registrar el contacto del cliente si lo necesitas." />
        <div className="sales-contact-fields">
          {branch.legacy ? <p role="status">Crea y configura una sucursal antes de registrar ventas.</p> :
            <MovementBranch state={branch} locked={draft.lines.length > 0} onChange={id => { setSearch(""); update({ branchId: id }); }} />}
          <label>Cliente opcional<input value={draft.customer} maxLength={150} onChange={event => update({ customer: event.target.value })} /></label>
          <label>Teléfono opcional<input type="tel" autoComplete="tel" maxLength={30} value={draft.customerPhone ?? ""}
            onChange={event => update({ customerPhone: event.target.value })} /></label>
          <label>Correo electrónico opcional<input ref={emailInput} type="email" autoComplete="email" maxLength={254}
            value={draft.customerEmail ?? ""} aria-invalid={emailInvalid}
            aria-describedby={emailInvalid ? "sales-email-error" : undefined}
            onChange={event => { setEmailInvalid(false); update({ customerEmail: event.target.value }); }} />
            {emailInvalid && <small id="sales-email-error" role="alert">Ingresa un correo electrónico válido.</small>}
          </label>
        </div></>}
    </section>
    <section className="sales-panel sales-work-panel">
      <PanelHeading icon={Package} title="Artículos" description={review ? "Cantidades y precios aplicados a esta venta." : "Busca en el catálogo y agrega las cantidades."} />
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
  const [reasonInvalid, setReasonInvalid] = useState(false);
  const reasonInput = useRef<HTMLTextAreaElement>(null);
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
    <Heading title={sale ? "Venta " + sale.movement.folio : "Comprobante de venta"} description="Comprobante interno de la operación.">
      <Link className="button secondary sales-back" to={path}><ArrowLeft size={16} aria-hidden="true" /> Volver a ventas</Link>
    </Heading>
    {error && <p className="notice error" role="alert">{error}</p>}
    {!sale ? !error && <p role="status">Cargando comprobante…</p> : <section className="sales-panel sales-work-panel">
      <div className="sales-document-heading"><PanelHeading icon={ReceiptText} title="Detalle de la venta" description="Artículos, precios y datos del comprobante." />
        {sale.movement.voidId ? <a className="sales-status-link" href="#anulacion"><SaleStatus voided /></a> : <SaleStatus voided={false} />}</div>
      <dl className="sales-details">
        <div><dt>Sucursal</dt><dd>{sale.sale.branchName}</dd></div>
        <div><dt>Fecha y hora</dt><dd><time className="sales-date">{receiptDate(sale.movement.date)}</time></dd></div>
        <div><dt>Responsable</dt><dd>{sale.movement.actor}</dd></div>
        {sale.sale.customer && <div><dt>Cliente</dt><dd>{sale.sale.customer}</dd></div>}
        {sale.sale.customerPhone && <div><dt>Teléfono</dt><dd>{sale.sale.customerPhone}</dd></div>}
        {sale.sale.customerEmail && <div><dt>Correo electrónico</dt><dd>{sale.sale.customerEmail}</dd></div>}
      </dl>
      <ul className="sales-lines">{sale.lines.map(line => <li key={line.itemId}>
        <div><strong>{line.name}</strong><small>{line.sku}</small></div>
        <span>{amount(line.quantity, line.unit)} · {money(line.unitPrice)} por {line.unit}</span><strong>{money(line.amount)}</strong>
      </li>)}</ul>
      <p className="sales-total">Total <strong>{money(sale.sale.total)}</strong></p>
      {sale.movement.voidId ? <section className="sales-void-summary" id="anulacion" aria-labelledby="sales-void-summary-title">
        <h3 id="sales-void-summary-title">Anulación {sale.movement.voidFolio}</h3>
        <dl><dt>Motivo</dt><dd>{sale.movement.voidReason}</dd></dl>
      </section> :
        canVoid && (voiding ? <form className="sales-void-form" aria-labelledby="sales-void-title" onSubmit={async event => {
          event.preventDefault();
          if (busy) return;
          if (!reason.trim()) {
            setReasonInvalid(true);
            reasonInput.current?.focus();
            return;
          }
          setError(""); setBusy(true);
          try {
            setSale(await request<Sale>(url + "/void", { method: "POST", body: JSON.stringify({ reason }) }));
            setVoiding(false);
          } catch (failure) { setError(failureMessage(failure)); }
          finally { setBusy(false); }
        }}>
          <div>
            <h3 id="sales-void-title">Anular esta venta</h3>
            <p id="sales-void-help">Las cantidades volverán a la sucursal original.</p>
          </div>
          <label>Motivo de anulación
            <textarea ref={reasonInput} autoFocus maxLength={150} rows={3} value={reason}
              placeholder="Escribe el motivo" aria-invalid={reasonInvalid}
              aria-describedby={reasonInvalid ? "sales-void-help sales-void-error" : "sales-void-help"}
              onChange={event => { setReasonInvalid(false); setReason(event.target.value); }} />
            {reasonInvalid && <small id="sales-void-error" role="alert">Escribe el motivo de la anulación.</small>}
          </label>
          <div className="sales-actions">
            <button className="button secondary" type="button" disabled={busy}
              onClick={() => { setVoiding(false); setReasonInvalid(false); }}>Cancelar</button>
            <button className="button danger" type="submit" disabled={busy}>{busy ? "Anulando…" : "Confirmar anulación"}</button>
          </div>
        </form> : <button type="button" className="button danger" onClick={() => setVoiding(true)}>Anular venta</button>)}
    </section>}
  </>;
}
