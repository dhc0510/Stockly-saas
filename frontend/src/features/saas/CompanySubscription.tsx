import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { LoaderCircle } from "lucide-react";
import { ApiError } from "../../lib/http";
import { crc, cycleLabel, day, download, methodLabel, saasApi, type Mine } from "./api";
import { StateBadge, errorMessage } from "./shared";
import "./saas.css";

// STK-018 · Lo que ve la empresa: franjas de aviso, ventana de límite, «Mi suscripción» e ingreso suspendido.
export const TRIAL_LIMIT_EVENT = "stockly:trial-limit";
const todayCR = () => new Intl.DateTimeFormat("en-CA", { timeZone: "America/Costa_Rica" }).format(new Date());
const daysUntil = (iso:string) => Math.round((Date.parse(iso + "T00:00:00Z") - Date.parse(todayCR() + "T00:00:00Z")) / 86_400_000);

/** Solo una respuesta con suscripción cuenta; cualquier otra cosa equivale a «sin suscripción». */
const valid = (v:Mine | undefined):Mine | null => v && typeof v === "object" && "subscription" in v && v.subscription ? v : null;

function useSubscription() {
  const { pathname } = useLocation(); const [data, setData] = useState<Mine | null>();
  useEffect(() => {
    let alive = true;
    const load = () => saasApi.mine().then(v => { if (alive) setData(valid(v)); }).catch(() => { if (alive) setData(null); });
    load(); window.addEventListener(TRIAL_LIMIT_EVENT, load);
    return () => { alive = false; window.removeEventListener(TRIAL_LIMIT_EVENT, load); };
  }, [pathname]);
  return data;
}

/** Franja para el administrador de la empresa: prueba (con lo usado), vencimiento próximo o gracia. */
export function SubscriptionBanner() {
  const data = useSubscription();
  if (!data) return null;
  const s = data.subscription;
  const left = daysUntil(s.expiresOn);
  if (s.state === "TRIAL") return <div className="saas-banner trial" role="status"><span>
      <strong>Prueba: {left <= 0 ? "termina hoy" : `te ${left === 1 ? "queda 1 día" : `quedan ${left} días`}`} (hasta el {day(s.trialEndsOn)}).</strong>
      Productos {s.usage.products}/{s.limits.products} · usuarios {s.usage.users}/{s.limits.users} · sucursales {s.usage.branches}/{s.limits.branches} · facturación electrónica incluida.</span>
    <Link className="button secondary" to="/subscription">Elegir un plan</Link></div>;
  if (s.state === "GRACE") return <div className="saas-banner grace" role="alert"><span>
      <strong>Tu {s.limited ? "prueba terminó" : "suscripción venció"} el {day(s.expiresOn)}.</strong>
      Tienes acceso completo hasta el {day(s.suspendsOn)}; ese día se suspende si no se registra el pago.</span>
    <Link className="button secondary" to="/subscription">Ver cómo pagar</Link></div>;
  if (s.state === "ACTIVE" && left <= s.noticeDays) return <div className="saas-banner due" role="status"><span>
      <strong>Tu suscripción vence {left <= 0 ? "hoy" : `en ${left === 1 ? "1 día" : `${left} días`}`} ({day(s.expiresOn)}).</strong>
      Paga a tiempo para seguir sin interrupciones.</span>
    <Link className="button secondary" to="/subscription">Ver cómo pagar</Link></div>;
  return null;
}

/** Ventana al llegar a un límite de la prueba (cualquier pantalla que reciba TRIAL_LIMIT). */
export function TrialLimitDialog({ admin }:{ admin:boolean }) {
  const [limit, setLimit] = useState<{ kind:string; max:string }>(); const [data, setData] = useState<Mine>();
  useEffect(() => {
    const open = (e:Event) => { const d = (e as CustomEvent<{ limit:string; max:string }>).detail; setLimit({ kind: d.limit, max: d.max });
      saasApi.mine().then(v => setData(valid(v) ?? undefined)).catch(() => setData(undefined)); };
    window.addEventListener(TRIAL_LIMIT_EVENT, open); return () => window.removeEventListener(TRIAL_LIMIT_EVENT, open);
  }, []);
  if (!limit) return null;
  const what = limit.kind === "USERS" ? "usuarios" : limit.kind === "BRANCHES" ? "sucursales" : "productos";
  return <div className="saas-limit-backdrop" role="presentation" onClick={e => { if (e.target === e.currentTarget) setLimit(undefined); }}>
    <section className="confirmation saas-limit" role="dialog" aria-modal="true" aria-labelledby="limit-title">
      <div className="dialog-body saas-dialog-body"><h2 id="limit-title">Llegaste al límite de la prueba</h2>
        <p>La prueba permite hasta <strong>{limit.max} {what}</strong>. Lo que ya cargaste se conserva; con un plan pagado no hay límites.</p>
        {data && data.subscription.plans.length > 0 && <div className="saas-plans">{data.subscription.plans.map(p => <div key={p.id} className="saas-plan">
          <strong>{p.name}</strong><span className="price">{crc.format(p.monthlyPrice)}<small> / mes</small></span>
          <small>{p.annualPrice ? `o ${crc.format(p.annualPrice)} al año · ` : ""}IVA incluido</small></div>)}</div>}
        {admin ? data?.subscription.paymentInstructions
            ? <div className="saas-callout saas-instructions">{data.subscription.paymentInstructions}{"\n"}Activamos tu plan al confirmar el pago.</div>
            : <div className="saas-callout">Comunícate con Stockly para elegir tu plan.</div>
          : <div className="saas-callout">Pide al administrador de tu empresa que elija un plan.</div>}
      </div>
      <footer className="dialog-actions"><button type="button" className="button primary" autoFocus onClick={() => setLimit(undefined)}>Entendido</button></footer>
    </section></div>;
}

/** «Mi suscripción»: estado, próximo pago, instrucciones, facturas (solo el administrador de la empresa). */
export function MySubscriptionPage() {
  const [data, setData] = useState<Mine | null>(); const [error, setError] = useState<unknown>(); const [downloadError, setDownloadError] = useState("");
  useEffect(() => { saasApi.mine().then(v => setData(valid(v))).catch(setError); }, []);
  if (error) return <div className="state-panel saas-state error" role="alert"><h2>No pudimos cargar tu suscripción</h2><p>{errorMessage(error)}</p></div>;
  if (data === undefined) return <div className="state-panel" role="status"><LoaderCircle className="spin" size={22}/> Cargando…</div>;
  if (data === null) return <div className="state-panel saas-state" role="status"><h2>Sin suscripción registrada</h2></div>;
  const s = data.subscription;
  const invoice = (paymentId:string) => data.documents.find(d => d.paymentId === paymentId && d.docType === "01");
  const files = (paymentId:string) => { const d = invoice(paymentId); return d ? <>
    <button type="button" className="inventory-text-button" onClick={() => download(saasApi.myDocumentUrl(d.id, "pdf")).catch(e => setDownloadError(errorMessage(e)))}>PDF</button>
    {" · "}<button type="button" className="inventory-text-button" onClick={() => download(saasApi.myDocumentUrl(d.id, "xml")).catch(e => setDownloadError(errorMessage(e)))}>XML</button></> : <small>En proceso</small>; };
  const usage = (label:string, used:number, max:number) => <div><div className="row"><span>{label}</span><span>{used}/{max}</span></div>
    <div className={`saas-meter${used >= max ? " full" : ""}`}><i style={{ width: `${Math.min(100, used / max * 100)}%` }}/></div></div>;
  return <section className="saas-page">
    <div className="page-heading"><div><p className="eyebrow">MI EMPRESA</p><h1>Mi suscripción</h1><p className="subtitle">Solo el administrador de la empresa ve esta pantalla.</p></div></div>
    <div className="saas-grid">
      <section className="panel saas-panel"><div className="saas-panel-head"><h2>Estado</h2><StateBadge state={s.state}/></div>
        <dl className="saas-list">
          <div><dt>Plan</dt><dd>{s.planName ? `${s.planName} · ${cycleLabel(s.cycle)} · ${crc.format(s.price ?? 0)}` : "Prueba"}</dd></div>
          <div><dt>{s.limited ? "Prueba hasta" : "Pagado hasta"}</dt><dd>{day(s.expiresOn)}</dd></div>
          {s.price != null && s.state !== "CANCELLED" && <div><dt>Próximo pago</dt><dd>{crc.format(s.price)} antes del {day(s.expiresOn)}</dd></div>}
          {s.state === "GRACE" && <div><dt>Se suspende el</dt><dd>{day(s.suspendsOn)}</dd></div>}
        </dl>
        {s.limited && <div className="saas-usage">{usage("Productos", s.usage.products, s.limits.products)}{usage("Usuarios", s.usage.users, s.limits.users)}
          {usage("Sucursales", s.usage.branches, s.limits.branches)}</div>}
        {s.plans.length > 0 && <><h3>Planes</h3><div className="saas-plans">{s.plans.map(p => <div key={p.id} className="saas-plan"><strong>{p.name}</strong>
          <span className="price">{crc.format(p.monthlyPrice)}<small> / mes</small></span>
          <small>{p.annualPrice ? `o ${crc.format(p.annualPrice)} al año · ` : ""}IVA incluido</small>{p.description && <small>{p.description}</small>}</div>)}</div></>}
        <h3>Cómo pagar</h3>
        <div className="saas-callout saas-instructions">{s.paymentInstructions ?? "Comunícate con Stockly para coordinar el pago."}</div>
      </section>
      <section className="panel saas-panel"><div className="saas-panel-head"><h2>Pagos y facturas</h2></div>
        {s.payments.length === 0 ? <p className="saas-muted">Todavía no hay pagos registrados.</p> : <>
          <table className="saas-table saas-wide"><thead><tr><th>Fecha</th><th>Período</th><th className="num">Monto</th><th>Factura</th></tr></thead>
            <tbody>{s.payments.map(p => <tr key={p.id}><td>{day(p.paidOn)}<small>{methodLabel[p.method]}</small></td><td>{day(p.coversFrom)} – {day(p.coversUntil)}</td>
              <td className="num">{crc.format(p.amount)}</td><td>{files(p.id)}</td></tr>)}</tbody></table>
          <div className="saas-cards">{s.payments.map(p => <article key={p.id} className="saas-card"><div className="saas-card-head"><strong>{day(p.paidOn)}</strong><strong>{crc.format(p.amount)}</strong></div>
            <small>{methodLabel[p.method]} · {day(p.coversFrom)} – {day(p.coversUntil)}</small><div>{files(p.id)}</div></article>)}</div></>}
        {downloadError && <p className="saas-danger" role="alert">{downloadError}</p>}
        <p className="saas-note">Para cambiar de plan o de ciclo, escríbenos; se aplica en tu próximo pago.</p>
      </section>
    </div>
  </section>;
}

/** Ingreso con la suscripción suspendida (código SUBSCRIPTION_SUSPENDED del servidor). */
export function SuspendedLogin({ error, onBack }:{ error:ApiError; onBack:()=>void }) {
  const admin = error.fieldErrors.role === "ADMIN";
  return <div className="saas-suspended" role="alert">
    <span className="saas-suspended-icon" aria-hidden="true">!</span>
    <h2>{admin ? "Tu suscripción está suspendida" : "El acceso de tu empresa está suspendido"}</h2>
    <p>{error.message}</p>
    {admin && error.fieldErrors.instructions && <div className="saas-callout saas-instructions">{error.fieldErrors.instructions}{"\n"}Al registrar el pago se reactiva el acceso.</div>}
    <button type="button" className="button secondary" onClick={onBack}>Volver al inicio de sesión</button>
  </div>;
}

export const isSuspension = (e:unknown):e is ApiError => e instanceof ApiError && e.code === "SUBSCRIPTION_SUSPENDED";
