import { useEffect, useState } from "react";
import { AlertTriangle, BarChart3, CalendarDays, Wallet } from "lucide-react";
import { useMovementBranch } from "../inventory/MovementBranch";
import { costaRicaDay, shortDay } from "../history/historyApi";
import { failureMessage } from "../receipts/receiptApi";
import { request } from "../../lib/http";
import { Heading, PanelHeading } from "./SalesPage";
import { METHODS, methodHints, methodLabels, type Method, type MethodState } from "./payments";
import "../receipts/receipts.css";
import "./sales.css";

const api = "/api/sales";
const through: Record<Method, string> = { CASH: "en efectivo", CARD: "con tarjeta", SINPE: "por SINPE Móvil", TRANSFER: "por transferencia" };
const currency = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC" });
const money = (value: number) => currency.format(value);

type MethodTotal = { method: Method; label: string; collected: number; refunded: number; net: number };
type Summary = { from: string; to: string; methods: MethodTotal[]; days: { day: string; methods: number[]; total: number }[]; sales: number; refunds: number };

/** STK-012 · Resumen de cobros: cobrado, devuelto y neto por medio y por día. No es un cierre de caja. */
export function PaymentSummaryPage() {
  const today = costaRicaDay(Date.now());
  const [branchId, setBranchId] = useState("");
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [summary, setSummary] = useState<Summary>();
  const [error, setError] = useState("");
  const { branches } = useMovementBranch(api);
  const rangeProblem = !from || !to ? "Elige las dos fechas del período." : from > to ? "La fecha inicial no puede ser posterior a la final."
    : to > today ? "No se aceptan fechas futuras." : "";
  useEffect(() => {
    if (rangeProblem) return;
    const controller = new AbortController();
    setSummary(undefined); setError("");
    request<Summary>(api + "/payments/summary?" + new URLSearchParams({ branchId, from, to }), { signal: controller.signal })
      .then(setSummary).catch(failure => { if (!controller.signal.aborted) setError(failureMessage(failure)); });
    return () => controller.abort();
  }, [branchId, from, to, rangeProblem]);
  const total = (key: "collected" | "refunded" | "net") => summary?.methods.reduce((sum, method) => sum + method[key], 0) ?? 0;

  return <div className="catalog-page sales-page">
    <Heading title="Resumen de cobros" description="Lo que entró, salió y quedó por medio de pago y por día. No incluye apertura ni cierre de caja." />
    <section className="sales-panel sales-work-panel" aria-label="Filtros del resumen">
      <div className="sales-summary-filters">
        <label>Sucursal<select value={branchId} onChange={event => setBranchId(event.target.value)}>
          <option value="">Todas</option>{branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
        </select></label>
        <label>Desde<input type="date" value={from} max={today} onChange={event => setFrom(event.target.value)} /></label>
        <label>Hasta<input type="date" value={to} max={today} onChange={event => setTo(event.target.value)} /></label>
      </div>
      {rangeProblem && <p className="sales-pay-error" role="alert">{rangeProblem}</p>}
    </section>
    {error && <p className="notice error" role="alert">{error}</p>}
    {!rangeProblem && !error && (!summary ? <p role="status">Cargando resumen…</p> :
      !summary.sales && !summary.refunds ? <section className="sales-panel sales-empty">
        <span className="sales-icon" aria-hidden="true"><Wallet size={22} /></span>
        <h3>Sin cobros ni devoluciones en este período</h3><p>Prueba con otras fechas o con otra sucursal.</p>
      </section> : <>
        <section className="sales-panel sales-work-panel">
          <PanelHeading icon={BarChart3} title="Totales del período" description={shortDay(summary.from) + " al " + shortDay(summary.to)} />
          <dl className="sales-flow">
            <div><dt>Entró</dt><dd>{money(total("collected"))}</dd><small>{summary.sales} {summary.sales === 1 ? "venta cobrada" : "ventas cobradas"}</small></div>
            <div><dt>Salió (devoluciones)</dt><dd>{money(total("refunded"))}</dd><small>{summary.refunds} {summary.refunds === 1 ? "venta anulada" : "ventas anuladas"}</small></div>
            <div className="sales-flow-result"><dt>Quedó</dt><dd>{money(total("net"))}</dd><small>Entró menos salió</small></div>
          </dl>
          <div className="sales-method-table" role="table" aria-label="Dinero por medio de pago">
            <div className="sales-method-row sales-method-head" role="row">
              <span role="columnheader">Medio</span><span role="columnheader">Entró</span>
              <span role="columnheader">Salió</span><span role="columnheader">Quedó</span>
            </div>
            {summary.methods.map(method => <div className={"sales-method-row" + (method.net < 0 ? " sales-method-negative" : "")} role="row" key={method.method}>
              <strong role="rowheader">{method.label}</strong>
              <span role="cell"><small className="sales-day-label">Entró</small>{money(method.collected)}</span>
              <span role="cell"><small className="sales-day-label">Salió</small>{money(method.refunded)}</span>
              <strong role="cell"><small className="sales-day-label">Quedó</small>{money(method.net)}</strong>
            </div>)}
          </div>
          {summary.methods.filter(method => method.net < 0).map(method => <p className="sales-negative-note" role="note" key={method.method}>
            <AlertTriangle size={16} aria-hidden="true" />
            <span><strong>{method.label}:</strong> devolviste {money(-method.net)} más {through[method.method]} de lo que cobraste {through[method.method]}.
              Pasa cuando una venta pagada con otro medio se devuelve por este.</span>
          </p>)}
          <p className="sales-summary-note">Lo que entró cuenta el día de la venta y lo que salió, el día en que se anuló.
            En efectivo se cuenta lo que quedó en caja, sin el vuelto.</p>
        </section>
        <section className="sales-panel sales-work-panel">
          <PanelHeading icon={CalendarDays} title="Lo que quedó por día" description="Entró menos salió, separado por día y medio de pago." />
          <div className="sales-day-table" role="table" aria-label="Lo que quedó por día">
            <div className="sales-day-row sales-day-head" role="row">
              <span role="columnheader">Día</span>
              {METHODS.map(method => <span role="columnheader" key={method}>{methodLabels[method]}</span>)}
              <span role="columnheader">Total</span>
            </div>
            {summary.days.map(day => <div className="sales-day-row" role="row" key={day.day}>
              <strong role="rowheader">{shortDay(day.day)}</strong>
              {day.methods.map((value, index) => <span role="cell" key={METHODS[index]}>
                <small className="sales-day-label">{methodLabels[METHODS[index]]}</small>{money(value)}</span>)}
              <strong role="cell"><small className="sales-day-label">Total</small>{money(day.total)}</strong>
            </div>)}
          </div>
        </section>
      </>)}
  </div>;
}

/** STK-012 · Medios de pago: solo el administrador elige qué medios ofrece la empresa al cobrar. */
export function PaymentMethodsPage() {
  const [methods, setMethods] = useState<MethodState[]>();
  const [message, setMessage] = useState<{ text: string; error: boolean }>();
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    request<MethodState[]>(api + "/payment-methods", { signal: controller.signal }).then(setMethods)
      .catch(failure => { if (!controller.signal.aborted) setMessage({ text: failureMessage(failure), error: true }); });
    return () => controller.abort();
  }, []);
  async function toggle(method: Method, active: boolean) {
    if (!methods || busy) return;
    if (!active && methods.filter(option => option.active).length === 1) {
      setMessage({ text: "Debe quedar al menos un medio de pago activo.", error: true });
      return;
    }
    const previous = methods;
    const next = Object.fromEntries(methods.map(option => [option.method, option.method === method ? active : option.active]));
    // Se muestra el cambio de inmediato y se revierte si el servidor lo rechaza.
    setMethods(methods.map(option => option.method === method ? { ...option, active } : option));
    setBusy(true);
    try {
      setMethods(await request<MethodState[]>(api + "/payment-methods", { method: "PUT", body: JSON.stringify(next) }));
      setMessage({ text: methodLabels[method] + (active ? " activado." : " desactivado."), error: false });
    } catch (failure) { setMethods(previous); setMessage({ text: failureMessage(failure), error: true }); }
    finally { setBusy(false); }
  }
  return <div className="catalog-page sales-page">
    <Heading title="Medios de pago" description="Elige qué medios ofrece tu empresa al cobrar. Solo administradores." />
    {message && <p className={"notice " + (message.error ? "error" : "success")} role={message.error ? "alert" : "status"}>{message.text}</p>}
    <section className="sales-panel sales-work-panel" aria-label="Medios de pago" aria-busy={busy}>
      {!methods ? !message && <p role="status">Cargando medios de pago…</p> : methods.map(option =>
        <label className="sales-method-toggle" key={option.method}>
          <span><strong>{option.label}</strong><small>{methodHints[option.method]}</small></span>
          <input type="checkbox" checked={option.active} disabled={busy} onChange={event => void toggle(option.method, event.target.checked)} />
        </label>)}
    </section>
    <p className="sales-summary-note">Desactivar un medio no cambia los cobros ya registrados. Se aplica a las ventas que se confirmen después.</p>
  </div>;
}
