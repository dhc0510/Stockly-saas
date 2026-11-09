import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { CreditCard, LoaderCircle } from "lucide-react";
import { addPeriods, crc, cycleLabel, day, download, methodLabel, saasApi, taxBreakdown, type Detail, type FiscalDocument, type Payment, type Plan } from "./api";
import { Dialog, GeneralError, StateBadge, errorMessage, fieldError, newRequestKey } from "./shared";

const docStatus:Record<FiscalDocument["status"], [string, string]> = { PENDING: ["Pendiente", "off"], PROCESSING: ["En Hacienda", "trial"],
  ACCEPTED: ["Aceptada", "active"], REJECTED: ["Rechazada", "suspended"] };
const todayCR = () => new Intl.DateTimeFormat("en-CA", { timeZone: "America/Costa_Rica" }).format(new Date());

// STK-018 · Suscripción de una empresa en su detalle (administrador de Stockly).
export function SubscriptionSection({ companyId, companyName }:{ companyId:string; companyName:string }) {
  const [detail, setDetail] = useState<Detail>(); const [error, setError] = useState<unknown>(); const [reload, setReload] = useState(0);
  const [dialog, setDialog] = useState<"pay" | "cancel" | Payment>(); const [toast, setToast] = useState("");
  useEffect(() => { setError(undefined); saasApi.detail(companyId).then(setDetail).catch(setError); }, [companyId, reload]);
  useEffect(() => { if (!toast) return; const t = window.setTimeout(() => setToast(""), 3500); return () => window.clearTimeout(t); }, [toast]);
  // Las facturas se emiten en segundo plano: mientras alguna esté pendiente se refresca cada pocos segundos.
  const pending = detail?.documents.some(d => d.status === "PENDING" || d.status === "PROCESSING")
    || detail?.subscription.payments.some(p => !p.voidedAt && !detail.documents.some(d => d.paymentId === p.id && d.docType === "01"));
  useEffect(() => { if (!pending) return; const t = window.setTimeout(() => setReload(v => v + 1), 5000); return () => window.clearTimeout(t); }, [pending, detail]);
  if (error) return <section className="panel saas-panel"><h2>Suscripción</h2><div className="notice error" role="alert">{errorMessage(error)}</div>
    <button type="button" className="button secondary" onClick={() => setReload(v => v + 1)}>Reintentar</button></section>;
  if (!detail) return <section className="panel saas-panel" role="status"><LoaderCircle className="spin" size={20}/> Cargando suscripción…</section>;
  const s = detail.subscription;
  const invoices = (p:Payment) => detail.documents.filter(d => d.paymentId === p.id);
  const callout = s.state === "GRACE" ? <>Venció el {day(s.expiresOn)}. Tiene acceso completo hasta el <strong>{day(s.suspendsOn)}</strong>; ese día se suspende si no se registra un pago.</>
    : s.state === "SUSPENDED" ? <>Suspendida desde el {day(s.suspendsOn)} por falta de pago. Los datos se conservan; un pago la reactiva.</>
    : s.state === "TRIAL" ? <>Prueba hasta el {day(s.trialEndsOn)} con límites de productos, usuarios y sucursales.</>
    : s.state === "CANCELLED" ? <>Cancelada: tiene acceso hasta el {day(s.expiresOn)} y luego se suspende. {s.cancelReason ? `Motivo: ${s.cancelReason}.` : ""}</>
    : null;
  return <section className="panel saas-panel saas-subscription" aria-labelledby="subscription-title">
    <div className="saas-panel-head"><h2 id="subscription-title"><CreditCard size={20}/> Suscripción <StateBadge state={s.state}/></h2>
      <div className="saas-actions"><button type="button" className="button primary" onClick={() => setDialog("pay")}>Registrar pago</button>
        {s.state !== "CANCELLED" && s.state !== "SUSPENDED" && <button type="button" className="button secondary" onClick={() => setDialog("cancel")}>Cancelar suscripción</button>}</div></div>
    {callout && <div className={`saas-callout${s.state === "SUSPENDED" ? " danger" : ""}`}>{callout}</div>}
    <dl className="saas-list">
      <div><dt>Plan</dt><dd>{s.planName ? `${s.planName} · ${cycleLabel(s.cycle)} · ${crc.format(s.price ?? 0)}` : "Prueba"}</dd></div>
      <div><dt>{s.paidUntil ? "Pagado hasta" : "Prueba hasta"}</dt><dd>{day(s.expiresOn)}</dd></div>
      {s.state !== "CANCELLED" && <div><dt>Fin de la gracia</dt><dd>{day(s.suspendsOn)}</dd></div>}
      <div><dt>Cliente desde</dt><dd>{day(s.createdAt)}</dd></div>
      <div><dt>Avisos enviados</dt><dd>{s.noticesSent.length ? s.noticesSent.map(day).join(", ") : "Ninguno"}</dd></div>
    </dl>
    <h3>Historial de pagos</h3>
    {s.payments.length === 0 ? <p className="saas-muted">Todavía no hay pagos registrados.</p> : <>
      <table className="saas-table saas-wide"><thead><tr><th>Fecha</th><th>Período</th><th className="num">Monto</th><th>Factura</th><th><span className="sr-only">Acciones</span></th></tr></thead>
        <tbody>{s.payments.map(p => <tr key={p.id} className={p.voidedAt ? "saas-voided" : undefined}>
          <td>{day(p.paidOn)}<small>{methodLabel[p.method]}{p.reference ? ` · ${p.reference}` : ""}{p.voidedAt ? ` · anulado por ${p.voidedBy}: ${p.voidReason}` : ""}</small></td>
          <td>{p.voidedAt ? "—" : `${day(p.coversFrom)} – ${day(p.coversUntil)}`}<small>{p.planName} · {cycleLabel(p.cycle)} × {p.periods}</small></td>
          <td className="num">{p.voidedAt ? <s>{crc.format(p.amount)}</s> : crc.format(p.amount)}</td>
          <td><Invoices docs={invoices(p)} voided={!!p.voidedAt} onRetried={() => { setToast("Comprobante reenviado a la cola."); setReload(v => v + 1); }}/></td>
          <td>{!p.voidedAt && <button type="button" className="inventory-text-button" onClick={() => setDialog(p)} aria-label={`Anular pago del ${day(p.paidOn)}`}>Anular</button>}</td></tr>)}</tbody></table>
      <div className="saas-cards">{s.payments.map(p => <article key={p.id} className={`saas-card${p.voidedAt ? " saas-voided" : ""}`}>
        <div className="saas-card-head"><strong>{day(p.paidOn)}</strong><strong>{p.voidedAt ? <s>{crc.format(p.amount)}</s> : crc.format(p.amount)}</strong></div>
        <small>{methodLabel[p.method]} · {p.voidedAt ? `anulado: ${p.voidReason}` : `${day(p.coversFrom)} – ${day(p.coversUntil)}`}</small>
        <Invoices docs={invoices(p)} voided={!!p.voidedAt} onRetried={() => setReload(v => v + 1)}/>
        {!p.voidedAt && <div className="saas-card-foot"><span/><button type="button" className="inventory-text-button" onClick={() => setDialog(p)}>Anular</button></div>}
      </article>)}</div></>}
    <p className="saas-note">Un pago no se borra: se anula con motivo y su factura se anula con una nota de crédito enviada a Hacienda.</p>
    {dialog === "pay" && <PaymentDialog companyId={companyId} companyName={companyName} detail={detail} onClose={() => setDialog(undefined)}
      onDone={(d, text) => { setDetail(d); setDialog(undefined); setToast(text); }}/>}
    {dialog === "cancel" && <ReasonDialog title={`Cancelar la suscripción de ${companyName}`} confirm="Cancelar suscripción"
      intro={<>Úsalo cuando el comercio avisa que deja Stockly. No se envían más avisos de cobro.</>}
      facts={[["Acceso", `Hasta el ${day(s.expiresOn)}`], ["Después", "Suspendida, con los datos conservados"], ["Volver", "Registrar un pago la reactiva"]]}
      note="Para borrar los datos se usa «Eliminar empresa», por separado." onClose={() => setDialog(undefined)}
      action={reason => saasApi.cancel(companyId, reason)} onDone={d => { setDetail(d); setDialog(undefined); setToast("Suscripción cancelada."); }}/>}
    {dialog && typeof dialog === "object" && <ReasonDialog title={`Anular pago del ${day(dialog.paidOn)}`} confirm="Anular pago"
      intro={<>{crc.format(dialog.amount)} por {methodLabel[dialog.method]}{dialog.reference ? ` (${dialog.reference})` : ""}. El período {day(dialog.coversFrom)} – {day(dialog.coversUntil)} deja de estar pagado.
        Si su factura fue aceptada, se emite una <strong>nota de crédito</strong> y se envía a Hacienda.</>}
      note="Si con esto la empresa queda vencida, entra en gracia o se suspende según las fechas." onClose={() => setDialog(undefined)}
      action={reason => saasApi.voidPayment(companyId, dialog.id, reason)} onDone={d => { setDetail(d); setDialog(undefined); setToast("Pago anulado."); }}/>}
    {toast && <div className="saas-toast" role="status">{toast}</div>}
  </section>;
}

function Invoices({ docs, voided, onRetried }:{ docs:FiscalDocument[]; voided:boolean; onRetried:()=>void }) {
  const [error, setError] = useState("");
  if (docs.length === 0) return <small className="saas-muted">{voided ? "Sin factura" : "En cola de emisión"}</small>;
  return <div className="saas-docs">{docs.map(d => { const [label, tone] = docStatus[d.status]; return <div key={d.id}>
    <span className={`saas-badge ${tone}`}>{d.docType === "03" ? "Nota de crédito · " : ""}{label}</span>
    <small>{d.environment === "TEST" ? "Pruebas · " : ""}…{d.consecutive.slice(-4)}
      {d.status === "ACCEPTED" && <> · <button type="button" className="inventory-text-button" onClick={() => download(saasApi.documentUrl(d.id, "pdf")).catch(e => setError(errorMessage(e)))}>PDF</button>
        · <button type="button" className="inventory-text-button" onClick={() => download(saasApi.documentUrl(d.id, "xml")).catch(e => setError(errorMessage(e)))}>XML</button></>}
      {d.status === "REJECTED" && <> · <button type="button" className="inventory-text-button" onClick={() => saasApi.retry(d.id).then(onRetried).catch(e => setError(errorMessage(e)))}>Reintentar</button></>}
    </small>
    {d.statusReason && d.status !== "ACCEPTED" && <small className={d.status === "REJECTED" ? "saas-danger" : "saas-muted"}>{d.statusReason}</small>}
  </div>; })}{error && <small className="saas-danger" role="alert">{error}</small>}</div>;
}

function PaymentDialog({ companyId, companyName, detail, onClose, onDone }:{ companyId:string; companyName:string; detail:Detail; onClose:()=>void;
  onDone:(d:Detail, text:string)=>void }) {
  const s = detail.subscription;
  const [plans, setPlans] = useState<Plan[]>(); const [loadError, setLoadError] = useState<unknown>();
  const [planId, setPlanId] = useState(s.planId ?? ""); const [cycle, setCycle] = useState<"MONTHLY" | "ANNUAL">(s.cycle ?? "MONTHLY");
  const [periods, setPeriods] = useState(1); const [amount, setAmount] = useState(""); const [paidOn, setPaidOn] = useState(todayCR());
  const [method, setMethod] = useState<"SINPE" | "TRANSFER" | "CASH">("SINPE"); const [reference, setReference] = useState("");
  const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(); const key = useRef(newRequestKey()); const guard = useRef(false);
  useEffect(() => { saasApi.plans().then(list => { const usable = list.filter(p => p.active || p.id === s.planId); setPlans(usable);
    if (!usable.some(p => p.id === planId) && usable[0]) setPlanId(usable[0].id); }).catch(setLoadError); }, []);
  const plan = plans?.find(p => p.id === planId);
  const price = plan ? (cycle === "ANNUAL" ? plan.annualPrice : plan.monthlyPrice) : null;
  const expected = price != null ? Math.round(price * periods * 100) / 100 : null;
  useEffect(() => { if (expected != null) setAmount(String(expected)); }, [expected]);
  useEffect(() => { if (plan && cycle === "ANNUAL" && !plan.annualPrice) setCycle("MONTHLY"); }, [plan, cycle]);
  // Vista previa del período con la misma regla del servidor (el definitivo lo calcula el servidor).
  const continues = !!s.paidUntil && (s.state === "ACTIVE" || s.state === "GRACE" || s.state === "CANCELLED");
  const from = continues ? s.paidUntil! : paidOn;
  const until = useMemo(() => from ? addPeriods(from, cycle, periods) : "", [from, cycle, periods]);
  const tax = expected != null ? taxBreakdown(expected) : null;
  async function submit(e:FormEvent) {
    e.preventDefault(); if (guard.current) return; guard.current = true; setBusy(true); setError(undefined);
    try {
      const d = await saasApi.pay(companyId, { requestKey: key.current, planId, cycle, periods, amount: amount ? Number(amount) : null, paidOn, method, reference });
      onDone(d, `Pago registrado. ${companyName} queda «${d.subscription.state === "ACTIVE" ? "Activa" : d.subscription.state}» hasta el ${day(d.subscription.expiresOn)}.`);
    } catch (err) { setError(err); } finally { guard.current = false; setBusy(false); }
  }
  return <Dialog title="Registrar pago" subtitle={companyName} busy={busy} wide onClose={onClose} onSubmit={submit}
    actions={<><button type="button" className="button secondary" onClick={onClose} disabled={busy}>Cancelar</button>
      <button type="submit" className="button primary" disabled={busy || !plans?.length}>{busy ? "Registrando…" : "Registrar pago"}</button></>}>
    <p className="saas-muted">Stockly no cobra: registra un pago que ya recibiste.</p>
    {loadError ? <div className="notice error" role="alert">{errorMessage(loadError)}</div>
      : !plans ? <p role="status"><LoaderCircle className="spin" size={18}/> Cargando planes…</p>
      : plans.length === 0 ? <div className="saas-callout">No hay planes disponibles. Crea uno en «Planes».</div> : <>
      <div className="saas-g2">
        <label className="saas-field">Plan<select value={planId} onChange={e => setPlanId(e.target.value)}>{plans.map(p => <option key={p.id} value={p.id}>{p.name}{p.active ? "" : " (archivado)"}</option>)}</select>{fieldError(error, "planId")}</label>
        <label className="saas-field">Ciclo<select value={cycle} onChange={e => setCycle(e.target.value as "MONTHLY" | "ANNUAL")}>
          <option value="MONTHLY">Mensual · {plan ? crc.format(plan.monthlyPrice) : ""}</option>
          {plan?.annualPrice ? <option value="ANNUAL">Anual · {crc.format(plan.annualPrice)}</option> : null}</select>{fieldError(error, "cycle")}</label>
      </div>
      <div className="saas-g3">
        <label className="saas-field">Períodos<input type="number" min={1} max={12} value={periods} onChange={e => setPeriods(Math.max(1, Math.min(12, Number(e.target.value) || 1)))}/>{fieldError(error, "periods")}</label>
        <label className="saas-field">Monto recibido (₡)<input inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} aria-invalid={!!fieldError(error, "amount")}/>{fieldError(error, "amount")}</label>
        <label className="saas-field">Fecha del pago<input type="date" max={todayCR()} value={paidOn} onChange={e => setPaidOn(e.target.value)} aria-invalid={!!fieldError(error, "paidOn")}/>{fieldError(error, "paidOn")}</label>
      </div>
      <div className="saas-g2">
        <label className="saas-field">Medio<select value={method} onChange={e => setMethod(e.target.value as "SINPE" | "TRANSFER" | "CASH")}>
          <option value="SINPE">SINPE Móvil</option><option value="TRANSFER">Transferencia</option><option value="CASH">Efectivo</option></select>{fieldError(error, "method")}</label>
        <label className="saas-field">Referencia<input value={reference} onChange={e => setReference(e.target.value)} maxLength={60} placeholder="Número de comprobante" aria-invalid={!!fieldError(error, "reference")}/>
          {!fieldError(error, "reference") && <small>{method === "CASH" ? "Opcional en efectivo." : "Obligatoria para SINPE y transferencia."}</small>}{fieldError(error, "reference")}</label>
      </div>
      {until && <div className="saas-callout info">Cubre del <strong>{day(from)} al {day(until)}</strong>.{" "}
        {continues ? "Como ya tenía un período pagado, sigue desde el vencimiento anterior." : "Empieza el día del pago."} La empresa queda «Activa».</div>}
      {tax && <div className="saas-callout">Se emitirá la <strong>factura electrónica de Stockly</strong> a {s.legalName} y se enviará a Hacienda:
        base {crc.format(tax.base)} + IVA 13 % {crc.format(tax.tax)} = {crc.format(expected!)}. El comercio la recibe por correo.</div>}
    </>}
    <GeneralError error={error}/>
  </Dialog>;
}

function ReasonDialog({ title, confirm, intro, facts, note, action, onClose, onDone }:{ title:string; confirm:string; intro:React.ReactNode;
  facts?:[string, string][]; note:string; action:(reason:string)=>Promise<Detail>; onClose:()=>void; onDone:(d:Detail)=>void }) {
  const [reason, setReason] = useState(""); const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(); const guard = useRef(false);
  async function submit(e:FormEvent) {
    e.preventDefault(); if (guard.current) return; guard.current = true; setBusy(true); setError(undefined);
    try { onDone(await action(reason)); } catch (err) { setError(err); } finally { guard.current = false; setBusy(false); }
  }
  return <Dialog title={title} busy={busy} onClose={onClose} onSubmit={submit}
    actions={<><button type="button" className="button secondary" onClick={onClose} disabled={busy}>Volver</button>
      <button type="submit" className="button danger" disabled={busy || !reason.trim()}>{busy ? "Procesando…" : confirm}</button></>}>
    <p>{intro}</p>
    {facts && <dl className="saas-list">{facts.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>}
    <label className="saas-field">Motivo<input value={reason} onChange={e => setReason(e.target.value)} maxLength={150} placeholder="Ej.: pago duplicado, cerró el negocio"/>
      <small>Obligatorio. Queda en el historial y en la actividad.</small>{fieldError(error, "reason")}</label>
    <div className="saas-callout">{note}</div>
    <GeneralError error={error}/>
  </Dialog>;
}
