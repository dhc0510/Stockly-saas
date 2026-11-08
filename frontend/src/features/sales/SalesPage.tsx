import { useEffect, useRef, useState } from "react";
import { Link, Route, Routes, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, ArrowRight, Building2, Check, ClipboardList, FileText, Package, Plus, ReceiptText, Search, Trash2, UserRound, Wallet, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { MovementBranch, useMovementBranch } from "../inventory/MovementBranch";
import { amount, failureMessage, receiptDate, type Article } from "../receipts/receiptApi";
import { ApiError, request } from "../../lib/http";
import {
  applied, balance, cents, methodHints, methodLabels, paymentProblem, paymentStateLabels, toAmount, toInput,
  type Method, type MethodState, type Payment, type PaymentDraft, type PaymentState, type Refund,
} from "./payments";
import { ID_TYPES, fiscalShortLabels, idProblem, idTypeLabel, type FiscalDocument, type FiscalStatus } from "./fiscal";
import { FiscalBadge, FiscalPanel } from "./FiscalPanel";
import { SaleReturnEditor, SaleReturnReceipt, SaleReturnsSection, useSaleReturns } from "./SaleReturnsPage";
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
  customerIdType?: string;
  customerIdNumber?: string;
  lines: Line[];
  payments?: PaymentDraft[];
  requestKey?: string;
};
type Row = { id: string; folio: string; date: string; branchName: string; customer: string | null; total: number; voided: boolean; payment: PaymentState;
  fiscalType: "01" | "04" | null; fiscalStatus: FiscalStatus | "NONE"; creditStatus: FiscalStatus | null; returnStatus?: "partial" | "complete" | null };
type Sale = {
  sale: { branchName: string; customer: string | null; customerPhone: string | null; customerEmail: string | null; total: number;
    customerIdType: string | null; customerIdNumber: string | null };
  movement: { folio: string; date: string; actor: string; voidId: string | null; voidReason: string | null; voidFolio: string | null };
  lines: { itemId: string; name: string; sku: string; unit: string; quantity: number; unitPrice: number; amount: number }[];
  payments: Payment[];
  refund: { date: string; actor: string; lines: Refund[] } | null;
  fiscal: FiscalDocument[];
};
const toCents = (value: number) => BigInt(Math.round(value * 100));
/** Medios activos de la empresa; vacío mientras carga. */
function usePaymentMethods() {
  const [methods, setMethods] = useState<MethodState[]>();
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    request<MethodState[]>(api + "/payment-methods", { signal: controller.signal }).then(setMethods)
      .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [version]);
  return { methods, error, reload: () => setVersion(value => value + 1) };
}

function PaymentBadge({ state }: { state: PaymentState }) {
  return <span className={"sales-status sales-payment-" + state}>{paymentStateLabels[state]}</span>;
}
const empty = (): Draft => ({ customer: "", lines: [] });
function load(key: string): Draft {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? "null");
    return value && Array.isArray(value.lines) && typeof value.customer === "string" ? value : empty();
  } catch { return empty(); }
}

export function Heading({ title, description, children }: { title: string; description: string; children?: React.ReactNode }) {
  return <header className="sales-heading">
    <div className="sales-heading-copy">
      <p className="sales-eyebrow"><ReceiptText size={15} aria-hidden="true" /> Operación comercial</p>
      <h1>{title}</h1>
      <p className="sales-description">{description}</p>
    </div>
    {children && <div className="sales-heading-action">{children}</div>}
  </header>;
}

function SaleStatus({ voided, returnStatus }: { voided: boolean; returnStatus?: Row["returnStatus"] }) {
  if (returnStatus) return <span className="sales-status sales-return-state">{returnStatus === "complete" ? "Devuelta por completo" : "Devuelta parcialmente"}</span>;
  const Icon = voided ? X : Check;
  return <span className={voided ? "sales-status sales-status-void" : "sales-status"}>
    <Icon size={13} aria-hidden="true" />{voided ? "Anulada" : "Confirmada"}
  </span>;
}

export function PanelHeading({ icon: Icon, title, description }: {
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
  const canReturn = admin || user.specialPermissions.includes("REGISTER_SALE_RETURNS");
  const canSeeHistory = admin || user.modules.some(module => module.moduleKey === "INVENTORY_HISTORY" && module.accessLevel !== "NONE");
  return <div className="catalog-page sales-page"><Routes>
    <Route index element={<List storage={storage} canWrite={canWrite} canVoid={canVoid} canReturn={canReturn} />} />
    {canWrite && <Route path="new" element={<Editor storage={storage} />} />}
    <Route path=":folio" element={<Document canVoid={canVoid} canWrite={canWrite} canReturn={canReturn} />} />
    {canReturn && <Route path=":folio/returns/new" element={<SaleReturnEditor storage={storage} />} />}
    <Route path=":folio/returns/:returnFolio" element={<SaleReturnReceipt canSeeHistory={canSeeHistory} />} />
  </Routes></div>;
}

function List({ storage, canWrite, canVoid, canReturn }: { storage: string; canWrite: boolean; canVoid: boolean; canReturn: boolean }) {
  const draft = load(storage);
  const [hasDraft, setHasDraft] = useState(!!(draft.lines.length || draft.customer.trim() || draft.customerPhone?.trim() || draft.customerEmail?.trim()));
  const [discard, setDiscard] = useState(false);
  const [search, setSearch] = useState("");
  const [branchId, setBranchId] = useState("");
  const [status, setStatus] = useState("");
  const [payment, setPayment] = useState("");
  const [fiscal, setFiscal] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<{ entries: Row[]; hasMore: boolean }>();
  const [error, setError] = useState("");
  const { branches } = useMovementBranch(api);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setResult(undefined);
    const params = new URLSearchParams({ search, branchId, status, payment, fiscal, page: String(page) });
    request<{ entries: Row[]; hasMore: boolean }>(api + "?" + params, { signal: controller.signal }).then(setResult)
      .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [search, branchId, status, payment, fiscal, page]);

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
            <option value="">Todos</option><option value="confirmed">Confirmadas</option><option value="voided">Anuladas</option><option value="partial">Devueltas parcialmente</option><option value="complete">Devueltas por completo</option>
          </select></label>
          <label>Cobro<select value={payment} onChange={event => { setPayment(event.target.value); setPage(0); }}>
            <option value="">Todos</option><option value="paid">Cobradas</option><option value="voided">Cobro anulado</option>
            <option value="none">Sin cobro registrado</option>
          </select></label>
          <label>Comprobante<select value={fiscal} onChange={event => { setFiscal(event.target.value); setPage(0); }}>
            <option value="">Todos</option><option value="ACCEPTED">Aceptados</option><option value="PENDING">Pendientes de envío</option>
            <option value="PROCESSING">En revisión</option><option value="REJECTED">Rechazados</option><option value="NONE">Sin comprobante</option>
          </select></label>
        </div>
      </div>
      {error ? <p className="sales-list-message" role="alert">{error}</p> :
        !result ? <p className="sales-list-message" role="status">Cargando ventas…</p> :
        !result.entries.length ? <div className="sales-empty">
          <span className="sales-icon" aria-hidden="true"><ReceiptText size={22} /></span>
          <h3>No hay ventas para esta consulta</h3>
          <p>{search || branchId || status || payment || fiscal ? "Prueba con otros filtros." : "Los comprobantes aparecerán aquí al confirmar una venta."}</p>
        </div> : <>
          <div className="sales-register-head sales-row-grid" aria-hidden="true">
            <span>Venta</span><span>Cliente</span><span>Sucursal</span><span>Fecha y hora</span>
            <span className="sales-amount">Total</span><span>Estado</span><span>Cobro</span><span>Comprobante</span><span>Acción</span>
          </div>
          <ul className="sales-list" role="list">
            {result.entries.map(sale => <li key={sale.id} className="sales-row sales-row-grid">
              <span className="sales-folio"><span className="sales-folio-icon" aria-hidden="true"><FileText size={16} /></span>
                <Link className="sales-row-link" to={path + "/" + sale.folio}><strong>{sale.folio}</strong></Link></span>
              <span className="sales-row-customer">{sale.customer || "Público general"}</span>
              <span className="sales-row-branch"><Building2 size={14} aria-hidden="true" />{sale.branchName}</span>
              <time className="sales-date" dateTime={sale.date}>{receiptDate(sale.date)}</time>
              <strong className="sales-amount">{money(sale.total)}</strong>
              <SaleStatus voided={sale.voided} returnStatus={sale.returnStatus} />
              <PaymentBadge state={sale.payment} />
              <span className="sales-row-fiscal"><FiscalBadge status={sale.fiscalStatus ?? "NONE"} prefix={sale.fiscalType ? fiscalShortLabels[sale.fiscalType] : undefined} />
                {sale.creditStatus && <FiscalBadge status={sale.creditStatus} prefix="Nota de crédito" />}</span>
              <span className="sales-row-action">{canReturn && sale.returnStatus === "partial" ? <Link className="button secondary sales-void-link"
                  to={path + "/" + sale.folio + "/returns/new?restante=1"}>Devolver lo restante</Link>
                : canVoid && !sale.voided && !sale.returnStatus
                ? <Link className="button secondary sales-void-link" to={path + "/" + sale.folio + "?anular=1"}
                  aria-label={"Anular venta " + sale.folio}>Anular venta</Link>
                : <span className="sales-row-none" aria-hidden="true">—</span>}</span>
            </li>)}
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
  const [idError, setIdError] = useState("");
  const idInput = useRef<HTMLInputElement>(null);
  const emailInput = useRef<HTMLInputElement>(null);
  const [saving, setSaving] = useState(false);
  const [discard, setDiscard] = useState(false);
  const branch = useMovementBranch(api, draft.branchId);
  const branchId = branch.selected?.id;
  const pending = !!draft.requestKey;
  const total = draft.lines.reduce((sum, line) => sum + lineCents(line), 0n);
  const payments = draft.payments ?? [];
  const paymentMethods = usePaymentMethods();
  const [paymentIssues, setPaymentIssues] = useState<Record<string, string>>({});
  const covered = payments.length > 0 && balance(total, payments).due === 0n;
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
    // STK-013: con tipo de identificación se emite factura y se exigen número y nombre.
    if (draft.customerIdType) {
      const problem = idProblem(draft.customerIdType, draft.customerIdNumber ?? "");
      if (problem) { setIdError(problem); idInput.current?.focus(); return; }
      if (!draft.customer.trim()) { setIdError("La factura necesita el nombre del cliente."); return; }
    }
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
        customerIdType: attempt.customerIdType || null,
        customerIdNumber: attempt.customerIdType ? (attempt.customerIdNumber ?? "").replace(/\D/g, "") : null,
        lines: attempt.lines.map(line => ({ itemId: line.article.id, quantity: quantity(line), expectedPrice: line.price })),
        payments: (attempt.payments ?? []).map(payment => ({
          method: payment.method, amount: toAmount(cents(payment.amount) ?? 0n),
          reference: payment.method === "CASH" ? null : payment.reference.trim() || null,
        })),
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
      } else if (failure instanceof ApiError && failure.code === "SALE_PAYMENT_METHOD_INACTIVE") {
        // Un administrador desactivó un medio mientras se cobraba: se marca el pago para quitarlo.
        persist({ ...attempt, requestKey: undefined });
        setPaymentIssues(failure.fieldErrors);
        paymentMethods.reload();
      } else if (failure instanceof ApiError && ["SALE_INVALID", "SALE_NOT_READY", "SALE_PAYMENT_INVALID"].includes(failure.code)) {
        persist({ ...attempt, requestKey: undefined });
        if (failure.code === "SALE_PAYMENT_INVALID") setPaymentIssues(failure.fieldErrors);
        if (failure.fieldErrors.customerIdNumber || failure.fieldErrors.customer) { setReview(false); setIdError(failure.fieldErrors.customerIdNumber ?? failure.fieldErrors.customer); }
      }
    } finally { setSaving(false); }
  }

  return <>
    <Heading title={review ? "Revisar y cobrar" : "Nueva venta"} description={review ? "Verifica la venta y registra cómo pagó el cliente." : "Elige la sucursal y agrega los artículos de la venta."}>
      <Link className="button secondary sales-back" to={path}><ArrowLeft size={16} aria-hidden="true" /> Volver a ventas</Link>
    </Heading>
    {error && <p className="notice error" role="alert">{error}</p>}
    <section className="sales-panel sales-work-panel">
      {review ? <>
        <PanelHeading icon={UserRound} title={branch.selected?.name ?? "Sucursal seleccionada"} description="Datos de la venta" />
        <dl className="sales-details">
          <div><dt>Cliente</dt><dd>{draft.customer.trim() || "Público general"}</dd></div>
          {draft.customerIdType && <div><dt>{idTypeLabel(draft.customerIdType)}</dt><dd>{draft.customerIdNumber}</dd></div>}
          <div><dt>Comprobante</dt><dd>{draft.customerIdType ? "Factura electrónica" : "Tiquete electrónico"}</dd></div>
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
          <label>Tipo de identificación opcional<select value={draft.customerIdType ?? ""}
            onChange={event => { setIdError(""); update({ customerIdType: event.target.value || undefined, ...(event.target.value ? {} : { customerIdNumber: "" }) }); }}>
            <option value="">Sin identificar</option>{ID_TYPES.map(type => <option key={type.code} value={type.code}>{type.label}</option>)}
          </select></label>
          <label>Número de identificación<input ref={idInput} inputMode="numeric" maxLength={20} disabled={!draft.customerIdType}
            value={draft.customerIdNumber ?? ""} aria-invalid={!!idError} aria-describedby={idError ? "sales-id-error" : undefined}
            onChange={event => { setIdError(""); update({ customerIdNumber: event.target.value }); }} />
            {idError && <small id="sales-id-error" role="alert">{idError}</small>}
          </label>
        </div>
        <p className="sales-doc-type" aria-live="polite"><FileText size={16} aria-hidden="true" />{draft.customerIdType
          ? <span><strong>Se emitirá una factura electrónica.</strong> Hacienda exige el nombre y una identificación válida.</span>
          : <span><strong>Se emitirá un tiquete electrónico.</strong> Para factura, indica el tipo y el número de identificación.</span>}</p></>}
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
      {!review && <div className="sales-actions">
        <button className="button secondary" type="button" onClick={() => setDiscard(true)}>Descartar</button>
        <button className="catalog-primary" type="button" disabled={!draft.lines.length || !branchId || !!branch.problem} onClick={startReview}>Revisar y cobrar</button>
      </div>}
      {discard && <div><p>¿Descartar esta venta sin confirmar?</p><div className="sales-actions">
        <button className="button secondary" type="button" autoFocus onClick={() => setDiscard(false)}>Conservar</button>
        <button className="button danger" type="button" onClick={() => {
          try { localStorage.removeItem(storage); navigate(path); }
          catch (failure) { setError(failureMessage(failure)); }
        }}>Descartar borrador</button>
      </div></div>}
    </section>
    {review && <section className="sales-panel sales-work-panel" aria-label="Cobro">
      <PaymentPanel total={total} payments={payments} locked={saving || pending} state={paymentMethods} issues={paymentIssues}
        onChange={next => { setPaymentIssues({}); setError(""); persist({ ...draft, payments: next }); }} />
      <div className="sales-actions">
        <button className="button secondary" type="button" disabled={saving || pending} onClick={() => { setReview(false); setIssues({}); setError(""); }}>Volver a editar</button>
        <button className="catalog-primary" type="button" disabled={saving || (!pending && (!branchId || !!branch.problem || !covered))}
          onClick={() => void confirm()}>{saving ? "Confirmando…" : pending ? "Consultar y reintentar" : "Confirmar venta y cobro"}</button>
      </div>
      {!pending && !covered && <p className="sales-hint">Registra el pendiente para poder confirmar.</p>}
    </section>}
  </>;
}

/** Cobro al confirmar: medios activos, pagos combinados y el saldo Total/Pagado/Pendiente/Vuelto. */
function PaymentPanel({ total, payments, locked, state, issues, onChange }: {
  total: bigint; payments: PaymentDraft[]; locked: boolean; issues: Record<string, string>;
  state: ReturnType<typeof usePaymentMethods>; onChange: (payments: PaymentDraft[]) => void;
}) {
  const [method, setMethod] = useState<Method>();
  const [value, setValue] = useState("");
  const [reference, setReference] = useState("");
  const [problem, setProblem] = useState("");
  const amountInput = useRef<HTMLInputElement>(null);
  const active = state.methods?.filter(option => option.active) ?? [];
  const { paid, due, change } = balance(total, payments);
  const amounts = applied(total, payments);
  function choose(next: Method) {
    setMethod(next); setProblem(""); setReference("");
    setValue(due > 0n ? toInput(due) : "");
    window.setTimeout(() => amountInput.current?.focus());
  }
  function add(event: React.FormEvent) {
    event.preventDefault();
    if (!method) return;
    const next = { method, amount: value, reference: method === "CASH" ? "" : reference.trim() };
    const message = paymentProblem(total, payments, next);
    if (message) { setProblem(message); amountInput.current?.focus(); return; }
    onChange([...payments, next]);
    setMethod(undefined); setValue(""); setReference(""); setProblem("");
  }
  return <>
    <PanelHeading icon={Wallet} title="Cobro" description="Stockly registra cómo pagó el cliente; no procesa pagos." />
    {state.error && <p className="notice error" role="alert">{state.error}</p>}
    {!state.methods ? !state.error && <p role="status">Cargando medios de pago…</p> : !active.length
      ? <p className="notice error" role="alert">No hay medios de pago activos. Un administrador debe activar al menos uno.</p>
      : !locked && <>
        <div className="sales-methods" role="group" aria-label="Medio de pago">
          {active.map(option => <button key={option.method} type="button" className="button secondary"
            aria-pressed={method === option.method} onClick={() => choose(option.method)}>{option.label}</button>)}
        </div>
        {method ? <form className={"sales-pay-form" + (method === "CASH" ? " sales-pay-cash" : "")} onSubmit={add} noValidate>
          <p className="sales-pay-hint">{methodHints[method]}</p>
          <label>{method === "CASH" ? "Monto recibido" : "Monto"}
            <input ref={amountInput} inputMode="decimal" autoComplete="off" value={value} maxLength={20}
              aria-invalid={!!problem} aria-describedby={problem ? "sales-pay-error" : undefined}
              onChange={event => { setValue(event.target.value); setProblem(""); }} /></label>
          {method !== "CASH" && <label>Referencia opcional
            <input value={reference} maxLength={60} autoComplete="off" placeholder="N.º de comprobante o autorización"
              onChange={event => setReference(event.target.value)} /></label>}
          <div className="sales-pay-buttons">
            {method === "CASH" && due > 0n && <button type="button" className="button secondary" onClick={() => setValue(toInput(due))}>Monto exacto</button>}
            <button type="submit" className="catalog-primary">Agregar pago</button>
          </div>
          {problem && <p id="sales-pay-error" className="sales-pay-error" role="alert">{problem}</p>}
        </form> : due > 0n && <p className="sales-hint-left">Elige un medio de pago para registrar el monto.</p>}
      </>}
    {!!payments.length && <ul className="sales-payments">{payments.map((payment, index) => <li key={index}
      className={issues["payments." + index] ? "sales-payment-issue" : undefined}>
      <div><strong>{methodLabels[payment.method]}</strong>
        <small>{payment.method === "CASH" ? "Recibido " + money(Number(cents(payment.amount) ?? 0n) / 100) : payment.reference || "Sin referencia"}</small>
        {issues["payments." + index] && <small role="alert">{issues["payments." + index]}</small>}</div>
      <strong className="sales-amount">{money(Number(amounts[index]) / 100)}</strong>
      {!locked && <button type="button" className="icon-button" aria-label={"Quitar pago " + (index + 1) + " · " + methodLabels[payment.method]}
        onClick={() => onChange(payments.filter((_, other) => other !== index))}><Trash2 size={16} /></button>}
    </li>)}</ul>}
    <dl className="sales-balance" aria-live="polite">
      <div><dt>Total</dt><dd>{money(Number(total) / 100)}</dd></div>
      <div><dt>Pagado</dt><dd>{money(Number(paid) / 100)}</dd></div>
      <div className={due > 0n ? "sales-balance-due" : undefined}><dt>Pendiente</dt><dd>{money(Number(due) / 100)}</dd></div>
      <div className={change > 0n ? "sales-balance-change" : undefined}><dt>Vuelto</dt><dd>{money(Number(change) / 100)}</dd></div>
    </dl>
  </>;
}

function Document({ canVoid, canWrite, canReturn }: { canVoid: boolean; canWrite: boolean; canReturn: boolean }) {
  const { folio } = useParams();
  const [query] = useSearchParams();
  const returnState = useSaleReturns(folio);
  const returnContext = returnState.data;
  const returnStatus = returnContext?.returns?.length
    ? returnContext.lines.some(line => line.remaining > 0) ? "partial" : "complete"
    : null;
  const [sale, setSale] = useState<Sale>();
  const [voiding, setVoiding] = useState(query.get("anular") === "1");
  const [error, setError] = useState("");
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
    {!sale ? !error && <p role="status">Cargando comprobante…</p> : <>
      <section className="sales-panel sales-work-panel">
        <div className="sales-document-heading"><PanelHeading icon={ReceiptText} title="Detalle de la venta" description="Artículos, precios y datos del comprobante." />
          {sale.movement.voidId ? <a className="sales-status-link" href="#anulacion"><SaleStatus voided /></a> : <SaleStatus voided={false} returnStatus={returnStatus} />}</div>
        <dl className="sales-details">
          <div><dt>Sucursal</dt><dd>{sale.sale.branchName}</dd></div>
          <div><dt>Fecha y hora</dt><dd><time className="sales-date">{receiptDate(sale.movement.date)}</time></dd></div>
          <div><dt>Responsable</dt><dd>{sale.movement.actor}</dd></div>
          {sale.sale.customer && <div><dt>Cliente</dt><dd>{sale.sale.customer}</dd></div>}
          {sale.sale.customerIdType && <div><dt>{idTypeLabel(sale.sale.customerIdType)}</dt><dd>{sale.sale.customerIdNumber}</dd></div>}
          {sale.sale.customerPhone && <div><dt>Teléfono</dt><dd>{sale.sale.customerPhone}</dd></div>}
          {sale.sale.customerEmail && <div><dt>Correo electrónico</dt><dd>{sale.sale.customerEmail}</dd></div>}
        </dl>
        <ul className="sales-lines">{sale.lines.map(line => <li key={line.itemId}>
          <div><strong>{line.name}</strong><small>{line.sku}</small></div>
          <span>{amount(line.quantity, line.unit)} · {money(line.unitPrice)} por {line.unit}</span><strong>{money(line.amount)}</strong>
        </li>)}</ul>
        <p className="sales-total">Total <strong>{money(sale.sale.total)}</strong></p>
        {sale.movement.voidId && <section className="sales-void-summary" id="anulacion" aria-labelledby="sales-void-summary-title">
          <h3 id="sales-void-summary-title">Anulación {sale.movement.voidFolio}</h3>
          <dl><dt>Motivo</dt><dd>{sale.movement.voidReason}</dd></dl>
        </section>}
      </section>
      <PaymentSummary sale={sale} />
      {returnState.error && <p className="notice error" role="alert">{returnState.error}</p>}
      {returnContext?.returns && <SaleReturnsSection context={returnContext} canReturn={canReturn} />}
      <FiscalPanel api={api} folio={sale.movement.folio} documents={sale.fiscal ?? []} voided={!!sale.movement.voidId} canWrite={canWrite}
        hasEmail={!!sale.sale.customerEmail} onChange={next => setSale(next as Sale)}
        customer={{ name: sale.sale.customer, idType: sale.sale.customerIdType, idNumber: sale.sale.customerIdNumber }} />
      {!sale.movement.voidId && canVoid && returnContext && !returnStatus && (voiding
        ? <VoidForm sale={sale} url={url} onCancel={() => setVoiding(false)} onVoided={next => { setSale(next); setVoiding(false); returnState.reload(); }} />
        : <div className="sales-actions"><button type="button" className="button danger" onClick={() => setVoiding(true)}>Anular venta</button></div>)}
    </>}
  </>;
}

/** Sección Cobro del comprobante: pagos, recibido, vuelto y devolución si la venta se anuló. */
function PaymentSummary({ sale }: { sale: Sale }) {
  if (!sale.payments.length) return <section className="sales-panel sales-work-panel" aria-label="Cobro">
    <PanelHeading icon={Wallet} title="Cobro" description="Medios con los que pagó el cliente." />
    <p className="sales-payment-none" role="note">Sin cobro registrado. Esta venta se registró antes de habilitar Cobros.</p>
  </section>;
  const received = sale.payments.reduce((sum, payment) => sum + toCents(payment.received ?? payment.amount), 0n);
  const change = received - toCents(sale.sale.total);
  return <section className="sales-panel sales-work-panel" aria-label="Cobro">
    <div className="sales-document-heading"><PanelHeading icon={Wallet} title="Cobro" description="Medios con los que pagó el cliente." />
      <PaymentBadge state={sale.movement.voidId ? "voided" : "paid"} /></div>
    {sale.refund && <div className="sales-refund-note">
      <strong>Devolución registrada</strong> · <time className="sales-date">{receiptDate(sale.refund.date)}</time> · {sale.refund.actor}
      <ul>{sale.refund.lines.map((line, index) => <li key={index}>
        {methodLabels[line.method]} {money(line.amount)}{line.reference ? " · Ref. " + line.reference : ""}</li>)}</ul>
    </div>}
    <ul className="sales-payments">{sale.payments.map((payment, index) => <li key={index}>
      <div><strong>{methodLabels[payment.method]}</strong>
        <small>{payment.method === "CASH" ? "Recibido " + money(payment.received ?? payment.amount) : payment.reference || "Sin referencia"}</small></div>
      <strong className="sales-amount">{money(payment.amount)}</strong>
    </li>)}</ul>
    <dl className="sales-balance">
      <div><dt>Total</dt><dd>{money(sale.sale.total)}</dd></div>
      <div><dt>Recibido</dt><dd>{money(Number(received) / 100)}</dd></div>
      <div className={change > 0n ? "sales-balance-change" : undefined}><dt>Vuelto</dt><dd>{money(Number(change) / 100)}</dd></div>
      <div><dt>Pagos</dt><dd>{sale.payments.length}</dd></div>
    </dl>
  </section>;
}

type RefundDraft = { method: Method; amount: string; reference: string };
/** Anulación con motivo obligatorio y, si hubo cobro, la devolución que debe sumar exactamente lo cobrado. */
function VoidForm({ sale, url, onCancel, onVoided }: { sale: Sale; url: string; onCancel: () => void; onVoided: (sale: Sale) => void }) {
  const methods = usePaymentMethods();
  const charged = sale.payments.length > 0;
  const paid = sale.payments.reduce((sum, payment) => sum + toCents(payment.amount), 0n);
  const [reason, setReason] = useState("");
  const [refunds, setRefunds] = useState<RefundDraft[]>(() => sale.payments.map(payment =>
    ({ method: payment.method, amount: toInput(toCents(payment.amount)), reference: "" })));
  const [problem, setProblem] = useState("");
  const [busy, setBusy] = useState(false);
  const options = (methods.methods ?? []).filter(option => option.active || sale.payments.some(payment => payment.method === option.method));
  const values = refunds.map(refund => cents(refund.amount));
  const sum = values.reduce<bigint>((total, value) => total + (value ?? 0n), 0n);
  const invalidAmount = values.some(value => value === null);
  const difference = paid > sum ? paid - sum : sum - paid;
  const ready = !!reason.trim() && (!charged || (!invalidAmount && sum === paid && refunds.length > 0));
  const change = (index: number, next: Partial<RefundDraft>) =>
    setRefunds(refunds.map((refund, other) => other === index ? { ...refund, ...next } : refund));

  return <form className="sales-void-form" aria-labelledby="sales-void-title" noValidate onSubmit={async event => {
    event.preventDefault();
    if (busy) return;
    if (!reason.trim()) { setProblem("Escribe el motivo de la anulación."); return; }
    if (charged && invalidAmount) { setProblem("Revisa los montos: deben ser positivos y con hasta dos decimales."); return; }
    if (charged && sum !== paid) { setProblem("La devolución debe sumar exactamente lo cobrado (" + money(Number(paid) / 100) + ")."); return; }
    setProblem(""); setBusy(true);
    try {
      onVoided(await request<Sale>(url + "/void", { method: "POST", body: JSON.stringify({
        reason, refunds: refunds.map(refund => ({ method: refund.method, amount: toAmount(cents(refund.amount) ?? 0n),
          reference: refund.method === "CASH" ? null : refund.reference.trim() || null })),
      }) }));
    } catch (failure) { setProblem(failureMessage(failure)); }
    finally { setBusy(false); }
  }}>
    <div>
      <h3 id="sales-void-title">Anular esta venta</h3>
      <p id="sales-void-help">Las cantidades volverán a la sucursal original. {charged
        ? "Los cobros quedan anulados y se registra cómo se devolvió el dinero."
        : "La venta no tiene cobro registrado, así que no hay dinero que devolver."}</p>
    </div>
    <label>Motivo de la anulación · Obligatorio
      <textarea autoFocus maxLength={150} rows={3} value={reason} required placeholder="Ej. Cliente desistió de la compra"
        aria-describedby="sales-void-help" onChange={event => { setProblem(""); setReason(event.target.value); }} />
    </label>
    {charged && <fieldset className="sales-refunds">
      <legend>Devolución del dinero</legend>
      <p>Indica cómo le devolviste el dinero al cliente. Stockly no mueve dinero: solo deja constancia. Viene sugerido con los mismos medios del cobro.</p>
      <p className="sales-refund-charged">Cobrado: {sale.payments.map(payment => methodLabels[payment.method] + " " + money(payment.amount)).join(" · ")}</p>
      {methods.error && <p className="notice error" role="alert">{methods.error}</p>}
      {refunds.map((refund, index) => <div className="sales-refund-row" key={index}>
        <label>Medio<select value={refund.method} onChange={event => change(index, { method: event.target.value as Method })}>
          {(options.length ? options : [{ method: refund.method, label: methodLabels[refund.method], active: true }])
            .map(option => <option key={option.method} value={option.method}>{option.label}</option>)}
        </select></label>
        <label>Monto<input inputMode="decimal" autoComplete="off" maxLength={20} value={refund.amount}
          aria-invalid={values[index] === null} onChange={event => { setProblem(""); change(index, { amount: event.target.value }); }} /></label>
        {refund.method === "CASH" ? <span /> : <label>Referencia opcional<input maxLength={60} autoComplete="off" value={refund.reference}
          placeholder="N.º de transacción" onChange={event => change(index, { reference: event.target.value })} /></label>}
        <button type="button" className="button secondary" disabled={refunds.length === 1}
          aria-label={"Quitar devolución " + (index + 1)} onClick={() => setRefunds(refunds.filter((_, other) => other !== index))}>Quitar</button>
      </div>)}
      <button type="button" className="button secondary sales-refund-add" onClick={() => setRefunds([...refunds,
        { method: options[0]?.method ?? "CASH", amount: paid > sum ? toInput(paid - sum) : "", reference: "" }])}>
        <Plus size={16} aria-hidden="true" /> Agregar otro medio</button>
      <dl className="sales-balance" aria-live="polite">
        <div><dt>Cobrado</dt><dd>{money(Number(paid) / 100)}</dd></div>
        <div><dt>A devolver</dt><dd>{money(Number(sum) / 100)}</dd></div>
        <div className={difference > 0n ? "sales-balance-due" : undefined}><dt>Diferencia</dt><dd>{money(Number(difference) / 100)}</dd></div>
      </dl>
    </fieldset>}
    {problem && <p className="sales-pay-error" role="alert">{problem}</p>}
    <div className="sales-actions">
      <button className="button secondary" type="button" disabled={busy} onClick={onCancel}>Cancelar</button>
      <button className="button danger" type="submit" disabled={busy || !ready}>{busy ? "Anulando…" : "Confirmar anulación"}</button>
    </div>
  </form>;
}
