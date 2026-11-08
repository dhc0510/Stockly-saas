import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, FileText, Package, RotateCcw, Wallet, X } from "lucide-react";
import { ApiError, request } from "../../lib/http";
import { amount, failureMessage, receiptDate } from "../receipts/receiptApi";
import { Heading, PanelHeading } from "./SalesPage";
import { cents, methodLabels, toInput, type Method, type MethodState, type Payment, type Refund } from "./payments";
import "./sale-returns.css";

const salesPath = "/sales";
const api = "/api/sales";
const currency = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC" });
const money = (value: number) => currency.format(value);
const toCents = (value: number) => BigInt(Math.round(value * 100));
const fromCents = (value: bigint) => String(value / 100n) + "." + String(value % 100n).padStart(2, "0");
const decimal = (value: string) => value.trim().replace(",", ".");
const validQuantity = (value: string) => /^\d+(\.\d{1,3})?$/.test(value);
const reasons = ["Producto dañado", "Producto incorrecto", "El cliente cambió de opinión", "Otro"];
const detailLimit = 143;

type Available = {
  itemId: string; name: string; sku: string; unit: string; sold: number; returned: number; remaining: number; unitPrice: number;
};
type ReturnLine = { itemId: string; name: string; sku: string; unit: string; returned: number; restocked: number; amount: number };
type Fiscal = { status: string; reason: string | null; consecutive: string | null };
type ReturnRow = { folio: string; date: string; amount: number; refund: number; fiscal: Fiscal };
type Context = { saleId: string; saleFolio: string; branch: string; voided: boolean; lines: Available[]; payments: Payment[]; returns: ReturnRow[]; fiscal: Fiscal };
type Quote = { amount: number; refund: number; lines: ReturnLine[] };
type Receipt = {
  folio: string; saleFolio: string; branch: string; date: string; actor: string; reason: string;
  movementId: string | null; amount: number; lines: ReturnLine[]; refunds: Refund[]; fiscal: Fiscal;
};
type Quantity = { returned: string; restocked: string };
type RefundDraft = { method: Method; amount: string; reference: string };
type Selection = { itemId: string; returned: string; restocked: string };
type Confirmation = {
  requestKey: string; reason: string; detail: string; lines: Selection[]; expectedRefund: number;
  refunds: { method: Method; amount: string; reference: string | null }[];
};
type Pending = { input: Confirmation; quote: Quote };

const fiscalLabels: Record<string, string> = {
  NONE: "Sin comprobante electrónico", WAITING: "Esperando comprobante original", QUEUED: "Nota de crédito pendiente de emisión",
  PENDING: "Nota de crédito pendiente de envío", PROCESSING: "Nota de crédito en revisión", ACCEPTED: "Nota de crédito aceptada",
  REJECTED: "Nota de crédito rechazada", CANCELLED: "Nota de crédito cancelada", REVIEW: "Revisión fiscal requerida",
};

const saleUrl = (folio: string) => salesPath + "/" + encodeURIComponent(folio);
const returnsUrl = (folio: string) => api + "/" + encodeURIComponent(folio) + "/returns";
const returnUrl = (sale: string, folio: string) => saleUrl(sale) + "/returns/" + encodeURIComponent(folio);

export function useSaleReturns(folio?: string) {
  const [data, setData] = useState<Context>();
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  useEffect(() => {
    if (!folio) return;
    const controller = new AbortController();
    setData(undefined);
    setError("");
    request<Context>(returnsUrl(folio), { signal: controller.signal }).then(setData)
      .catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [folio, version]);
  return { data, error, reload: () => setVersion(value => value + 1) };
}

function ReturnFiscal({ fiscal }: { fiscal: Fiscal }) {
  const status = fiscal.status;
  const className = status === "REVIEW" || status === "REJECTED" ? " sales-return-issue" :
    status === "ACCEPTED" ? "" : " sales-status-void";
  return <div className="sales-return-fiscal">
    <span className={"sales-status" + className}>{fiscalLabels[status] ?? status}</span>
    {fiscal.consecutive && <small>{fiscal.consecutive}</small>}
    {fiscal.reason && <small>{fiscal.reason}</small>}
  </div>;
}

export function SaleReturnsSection({ context, canReturn }: { context: Context; canReturn: boolean }) {
  const hasReturns = context.returns.length > 0;
  const canAdd = canReturn && !context.voided && context.lines.some(line => line.remaining > 0);
  if (!canAdd && !hasReturns) return null;
  return <section className="sales-panel sales-work-panel" aria-label="Devoluciones">
    <div className="sales-return-heading">
      <PanelHeading icon={RotateCcw} title="Devoluciones" description="Consulta lo devuelto y lo que queda pendiente en esta venta." />
      {canAdd && <div className="sales-return-actions">
        {hasReturns && <Link className="button secondary" to={saleUrl(context.saleFolio) + "/returns/new?restante=1"}>Devolver lo restante</Link>}
        <Link className="button primary" to={saleUrl(context.saleFolio) + "/returns/new"}>Registrar devolución</Link>
      </div>}
    </div>
    {hasReturns && <>
      <ul className="sales-return-history">
        {context.returns.map(item => <li key={item.folio}>
          <Link to={returnUrl(context.saleFolio, item.folio)} className="sales-return-history-link">
            <strong>{item.folio}</strong><time dateTime={item.date}>{receiptDate(item.date)}</time>
          </Link>
          <div><strong className="sales-amount">{money(item.amount)}</strong><small>Reintegro {money(item.refund)}</small></div>
          <ReturnFiscal fiscal={item.fiscal} />
        </li>)}
      </ul>
      <ul className="sales-return-history">
        {context.lines.map(line => <li key={line.itemId}>
          <strong>{line.name}</strong>
          <span>Devuelto {amount(line.returned, line.unit)}</span>
          <span>Pendiente {amount(line.remaining, line.unit)}</span>
        </li>)}
      </ul>
    </>}
  </section>;
}

function readPending(key: string): Pending | null {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? "null");
    return typeof value?.input?.requestKey === "string" && Array.isArray(value.input.lines) && Array.isArray(value.quote?.lines) ? value : null;
  } catch { return null; }
}

function suggestedRefunds(payments: Payment[], refund: number): RefundDraft[] {
  let left = toCents(refund);
  const suggestions: RefundDraft[] = [];
  for (const payment of payments) {
    const paid = toCents(payment.amount);
    const use = paid < left ? paid : left;
    if (use > 0n) suggestions.push({ method: payment.method, amount: toInput(use), reference: "" });
    left -= use;
    if (left === 0n) break;
  }
  return suggestions;
}

export function SaleReturnEditor({ storage }: { storage: string }) {
  const { folio = "" } = useParams();
  const [search] = useSearchParams();
  const navigate = useNavigate();
  const state = useSaleReturns(folio);
  const context = state.data;
  const key = storage + ":return:" + folio;
  const [pending, setPending] = useState(() => readPending(key));
  const [values, setValues] = useState<Record<string, Quantity>>({});
  const [reason, setReason] = useState("");
  const [detail, setDetail] = useState("");
  const [quote, setQuote] = useState<Quote | null>(pending?.quote ?? null);
  const [refunds, setRefunds] = useState<RefundDraft[]>([]);
  const [methods, setMethods] = useState<MethodState[]>([]);
  const [issues, setIssues] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const remaining = search.get("restante") === "1";
  useEffect(() => {
    const controller = new AbortController();
    request<MethodState[]>(api + "/payment-methods", { signal: controller.signal }).then(setMethods)
      .catch(() => {});
    return () => controller.abort();
  }, []);
  const options = methods.length ? methods.filter(option =>
    option.active || context?.payments.some(payment => payment.method === option.method)).map(option => option.method) :
    [...new Set(context?.payments.map(payment => payment.method) ?? [])];

  const getValue = (line: Available): Quantity => !line.remaining ? { returned: "", restocked: "" } : values[line.itemId] ?? {
    returned: remaining && line.remaining ? String(line.remaining).replace(".", ",") : "",
    restocked: "",
  };
  function select(): Selection[] {
    const result: Selection[] = [];
    const problems: Record<string, string> = {};
    for (const line of context?.lines ?? []) {
      const value = getValue(line);
      const returned = decimal(value.returned) || "0";
      const restocked = decimal(value.restocked) || "0";
      if (!validQuantity(returned) || !validQuantity(restocked) || Number(returned) > line.remaining ||
          Number(restocked) > Number(returned) || (line.unit === "unidad" &&
          (!Number.isInteger(Number(returned)) || !Number.isInteger(Number(restocked))))) {
        problems[line.itemId] = "Revisa las cantidades. La reposición no puede superar lo devuelto.";
      } else if (Number(returned) > 0) {
        result.push({ itemId: line.itemId, returned, restocked });
      }
    }
    setIssues(problems);
    if (Object.keys(problems).length) throw new Error("Revisa los artículos marcados.");
    if (!result.length) throw new Error("Indica al menos una cantidad para devolver.");
    return result;
  }

  async function review() {
    setError("");
    try {
      if (!reason || (reason === "Otro" && !detail.trim())) throw new Error("Completa el motivo de la devolución.");
      const lines = select();
      setBusy(true);
      const result = await request<Quote>(returnsUrl(folio) + "/preview", { method: "POST", body: JSON.stringify({ lines }) });
      setQuote(result);
      setRefunds(suggestedRefunds(context?.payments ?? [], result.refund));
      window.scrollTo(0, 0);
    } catch (failure) {
      setError(failureMessage(failure));
      if (failure instanceof ApiError) setIssues(failure.fieldErrors);
    } finally { setBusy(false); }
  }

  async function confirm() {
    if (!quote || busy) return;
    setError("");
    let attempt = pending;
    try {
      if (!attempt) {
        const values = refunds.map(refund => cents(refund.amount));
        if (values.some(value => value === null) || values.reduce<bigint>((sum, value) => sum + (value ?? 0n), 0n) !== toCents(quote.refund))
          throw new Error("Los medios de reintegro deben sumar " + money(quote.refund) + ".");
        attempt = { quote, input: {
          requestKey: crypto.randomUUID(), reason, detail, lines: select(), expectedRefund: quote.refund,
          refunds: refunds.map((refund, index) => ({ method: refund.method, amount: fromCents(values[index]!),
            reference: refund.method === "CASH" ? null : refund.reference.trim() || null })),
        } };
        localStorage.setItem(key, JSON.stringify(attempt));
        setPending(attempt);
      }
      setBusy(true);
      const receipt = await request<Receipt>(returnsUrl(folio), { method: "POST", body: JSON.stringify(attempt.input) });
      localStorage.removeItem(key);
      navigate(returnUrl(receipt.saleFolio, receipt.folio), { replace: true });
    } catch (failure) {
      setError(failureMessage(failure));
      if (failure instanceof ApiError && [400, 409].includes(failure.status) && failure.code !== "RETURN_KEY_CONFLICT") {
        localStorage.removeItem(key);
        setPending(null);
        if (["RETURN_LINES", "RETURN_CHANGED", "RETURN_CONFLICT"].includes(failure.code)) {
          if (attempt) {
            setValues(Object.fromEntries(attempt.input.lines.map(line => [line.itemId, { returned: line.returned, restocked: line.restocked }])));
            setReason(attempt.input.reason);
            setDetail(attempt.input.detail);
          }
          setQuote(null);
          setIssues(failure.fieldErrors);
          state.reload();
        } else if (attempt && pending) {
          setRefunds(attempt.input.refunds.map(refund => ({ ...refund, reference: refund.reference ?? "" })));
          setReason(attempt.input.reason);
          setDetail(attempt.input.detail);
          setValues(Object.fromEntries(attempt.input.lines.map(line => [line.itemId, { returned: line.returned, restocked: line.restocked }])));
        }
      }
    } finally { setBusy(false); }
  }

  const editValue = (line: Available, next: Partial<Quantity>) => {
    setValues(previous => ({ ...previous, [line.itemId]: { ...getValue(line), ...next } }));
    setIssues(previous => ({ ...previous, [line.itemId]: "" }));
  };
  return <>
    <Heading title={quote ? "Revisar devolución" : "Registrar devolución"}
      description={"Venta " + folio + (context ? " · " + context.branch : "")}>
      <Link className="button secondary sales-back" to={saleUrl(folio)}><ArrowLeft size={16} aria-hidden="true" /> Volver a la venta</Link>
    </Heading>
    {(error || state.error) && <p className="notice error" role="alert">{error || state.error}</p>}
    {!context && !pending && !state.error && <p role="status">Cargando venta…</p>}
    {!quote && context && (context.voided || !context.lines.some(line => line.remaining > 0)) &&
      <p className="notice" role="status">Esta venta no tiene cantidades pendientes por devolver.</p>}
    {!quote && context && !context.voided && context.lines.some(line => line.remaining > 0) && <>
      <section className="sales-panel sales-work-panel">
        <div className="sales-return-heading">
          <PanelHeading icon={Package} title="Artículos de la venta" description="Indica cuánto recibiste y cuánto está apto para vender." />
          <button type="button" className="button secondary" onClick={() => setValues(Object.fromEntries(
            context.lines.map(line => [line.itemId, { ...getValue(line), restocked: getValue(line).returned }])))}>Reponer todo</button>
        </div>
        <div className="sales-return-items">
          {context.lines.map(line => {
            const value = getValue(line);
            return <article key={line.itemId} className="sales-return-item">
              <div><strong>{line.name}</strong><small>{line.sku} · {money(line.unitPrice)} por {line.unit}</small>
                <small>Pendiente {amount(line.remaining, line.unit)}</small></div>
              <label>Devuelve ({line.unit})
                <input aria-label={"Devuelve " + line.name} aria-invalid={!!issues[line.itemId]} maxLength={20}
                  inputMode={line.unit === "unidad" ? "numeric" : "decimal"} disabled={!line.remaining || busy} value={value.returned}
                  placeholder="0" onChange={event => editValue(line, { returned: event.target.value })} />
              </label>
              <label>Repone ({line.unit})
                <input aria-label={"Repone " + line.name} aria-invalid={!!issues[line.itemId]} maxLength={20}
                  inputMode={line.unit === "unidad" ? "numeric" : "decimal"} disabled={!line.remaining || busy} value={value.restocked}
                  placeholder="0" onChange={event => editValue(line, { restocked: event.target.value })} />
              </label>
              {issues[line.itemId] && <p className="sales-line-note" role="alert">{issues[line.itemId]}</p>}
            </article>;
          })}
        </div>
      </section>
      <section className="sales-panel sales-work-panel sales-return-reason">
        <PanelHeading icon={FileText} title="Motivo de la devolución" description="El motivo queda asociado al comprobante." />
        <label>Motivo<select value={reason} onChange={event => setReason(event.target.value)}>
          <option value="">Selecciona un motivo</option>{reasons.map(value => <option key={value}>{value}</option>)}
        </select></label>
        {reason === "Otro" && <label>Detalle<textarea value={detail} maxLength={detailLimit} rows={2}
          onChange={event => setDetail(event.target.value)} /></label>}
        <div className="sales-actions"><Link className="button secondary" to={saleUrl(folio)}>Cancelar</Link>
          <button type="button" className="catalog-primary" disabled={busy} onClick={() => void review()}>Revisar devolución</button></div>
      </section>
    </>}
    {quote && <>
      <section className="sales-panel sales-work-panel">
        <PanelHeading icon={Package} title="Artículos y reposición" description="Solo las cantidades aptas vuelven a la sucursal original." />
        <ReturnedLines lines={quote.lines} />
        <p className="sales-total">Valor devuelto <strong>{money(quote.amount)}</strong></p>
        <p>Motivo <strong>{pending?.input.reason ?? reason}{(pending?.input.reason ?? reason) === "Otro" ? " · " + (pending?.input.detail ?? detail) : ""}</strong></p>
      </section>
      <section className="sales-panel sales-work-panel">
        <div className="sales-return-heading">
          <PanelHeading icon={Wallet} title="Reintegro" description={quote.refund ? "Registra cómo devolviste el dinero al cliente." : "Esta devolución no requiere reintegro."} />
          {!pending && quote.refund > 0 && <button className="button secondary" type="button" disabled={!options.length || busy}
            onClick={() => setRefunds(previous => [...previous, { method: options[0], amount: "", reference: "" }])}>Añadir medio</button>}
        </div>
        {pending ? <p className="sales-return-note" role="status">Stockly conserva la clave del intento. Al reintentar, recupera el comprobante si ya fue registrado.</p> :
          refunds.map((refund, index) => {
            const change = (next: Partial<RefundDraft>) => setRefunds(previous => previous.map((other, current) =>
              current === index ? { ...other, ...next } : other));
            return <div className="sales-return-payment" key={index}>
              <label>Medio<select value={refund.method} disabled={busy} onChange={event => change({ method: event.target.value as Method })}>
                {options.map(method => <option key={method} value={method}>{methodLabels[method]}</option>)}
              </select></label>
              <label>Monto (₡)<input inputMode="decimal" value={refund.amount} maxLength={20} disabled={busy}
                onChange={event => change({ amount: event.target.value })} /></label>
              {refund.method !== "CASH" && <label>Referencia opcional<input maxLength={60} value={refund.reference} disabled={busy}
                onChange={event => change({ reference: event.target.value })} /></label>}
              <button type="button" className="button secondary" aria-label="Quitar medio" disabled={busy}
                onClick={() => setRefunds(previous => previous.filter((_, current) => current !== index))}><X size={16} aria-hidden="true" /></button>
            </div>;
          })}
        <p className="sales-total">Importe a reintegrar <strong>{money(quote.refund)}</strong></p>
        {context && <ReturnFiscal fiscal={context.fiscal} />}
        <div className="sales-actions">
          <button type="button" className="button secondary" disabled={busy || !!pending} onClick={() => { setQuote(null); setError(""); }}>Volver a editar</button>
          <button type="button" className="catalog-primary" disabled={busy} onClick={() => void confirm()}>
            {busy ? "Confirmando…" : pending ? "Consultar y reintentar" : "Confirmar devolución"}
          </button>
        </div>
      </section>
    </>}
  </>;
}

function ReturnedLines({ lines }: { lines: ReturnLine[] }) {
  return <ul className="sales-lines">{lines.map(line => <li key={line.itemId}>
    <div><strong>{line.name}</strong><small>{line.sku} · Devuelto {amount(line.returned, line.unit)}</small>
      <small>Repone {amount(line.restocked, line.unit)} · No apto {amount(Math.round((line.returned - line.restocked) * 1000) / 1000, line.unit)}</small></div>
    <strong className="sales-amount">{money(line.amount)}</strong>
  </li>)}</ul>;
}

export function SaleReturnReceipt({ canSeeHistory }: { canSeeHistory: boolean }) {
  const { folio = "", returnFolio = "" } = useParams();
  const [receipt, setReceipt] = useState<Receipt>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setReceipt(undefined);
    setError("");
    request<Receipt>(returnsUrl(folio) + "/" + encodeURIComponent(returnFolio), { signal: controller.signal })
      .then(setReceipt).catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [folio, returnFolio]);

  return <>
    <Heading title={"Devolución " + returnFolio} description={"Venta " + folio + (receipt ? " · " + receipt.branch : "")}>
      <Link className="button secondary sales-back" to={saleUrl(folio)}><ArrowLeft size={16} aria-hidden="true" /> Volver a la venta</Link>
    </Heading>
    {error ? <p className="notice error" role="alert">{error}</p> : !receipt ? <p role="status">Cargando devolución…</p> : <>
      <section className="sales-panel sales-work-panel">
        <PanelHeading icon={FileText} title="Detalle de la devolución" description="Comprobante interno de la operación." />
        <dl className="sales-details">
          <div><dt>Fecha y hora</dt><dd>{receiptDate(receipt.date)}</dd></div>
          <div><dt>Responsable</dt><dd>{receipt.actor}</dd></div>
          <div><dt>Motivo</dt><dd>{receipt.reason}</dd></div>
        </dl>
        <ReturnedLines lines={receipt.lines} />
        <p className="sales-total">Valor devuelto <strong>{money(receipt.amount)}</strong></p>
        {receipt.movementId && canSeeHistory && <Link to={"/inventory/history/movements/" + receipt.movementId}>Ver reposición en Historial</Link>}
      </section>
      <section className="sales-panel sales-work-panel">
        <PanelHeading icon={Wallet} title="Reintegro y comprobante" description="Consulta los medios registrados y el estado fiscal." />
        <ReturnFiscal fiscal={receipt.fiscal} />
        {receipt.refunds.length ? <ul className="sales-lines">{receipt.refunds.map((refund, index) => <li key={index}>
          <div><strong>{methodLabels[refund.method]}</strong>{refund.reference && <small>{refund.reference}</small>}</div>
          <strong>{money(refund.amount)}</strong>
        </li>)}</ul> : <p className="sales-return-note">Sin reintegro registrado.</p>}
      </section>
    </>}
  </>;
}
