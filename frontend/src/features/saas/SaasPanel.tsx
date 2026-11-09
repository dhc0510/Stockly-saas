import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { LoaderCircle } from "lucide-react";
import { crc, day, saasApi, type PanelView } from "./api";
import { StateBadge, errorMessage } from "./shared";

// STK-018 · Panel del administrador de Stockly.
export function SaasPanel() {
  const [data, setData] = useState<PanelView>(); const [error, setError] = useState<unknown>(); const [reload, setReload] = useState(0);
  useEffect(() => { setError(undefined); saasApi.panel().then(setData).catch(setError); }, [reload]);
  const month = new Intl.DateTimeFormat("es-CR", { month: "long", year: "numeric", timeZone: "America/Costa_Rica" }).format(new Date());
  return <section className="saas-page">
    <div className="page-heading"><div><p className="eyebrow">ADMINISTRACIÓN</p><h1>Panel</h1>
      <p className="subtitle">Estado de las suscripciones y pagos registrados. Stockly no procesa cobros: los pagos se registran a mano.</p></div></div>
    {error ? <div className="state-panel saas-state error" role="alert"><h2>No pudimos cargar el panel</h2><p>{errorMessage(error)}</p>
        <button type="button" className="button secondary" onClick={() => setReload(v => v + 1)}>Reintentar</button></div>
      : !data ? <div className="state-panel" role="status"><LoaderCircle className="spin" size={22}/> Cargando panel…</div> : <>
      <div className="saas-kpis">
        <div className="saas-kpi"><span>Empresas activas</span><strong>{data.panel.states.ACTIVE}</strong></div>
        <div className="saas-kpi"><span>En prueba</span><strong>{data.panel.states.TRIAL}</strong></div>
        <div className="saas-kpi"><span>En gracia</span><strong>{data.panel.states.GRACE}</strong><small>Se suspenden si no pagan</small></div>
        <div className="saas-kpi"><span>Suspendidas</span><strong>{data.panel.states.SUSPENDED}</strong><small>Por falta de pago · {data.panel.states.CANCELLED} canceladas</small></div>
      </div>
      <div className="saas-grid">
        <section className="panel saas-panel"><div className="saas-panel-head"><h2>Requieren atención</h2><small>Próximos días</small></div>
          {data.panel.attention.length === 0 ? <p className="saas-muted">Nada pendiente por ahora.</p> : <ul className="saas-attention">
            {data.panel.attention.map((a, i) => <li key={i}>
              <div><strong>{a.companyName}</strong><small>{a.planName ? `${a.planName} · ` : ""}{a.reason} · {day(a.date)}</small></div>
              {a.state === "INVOICE_REJECTED" ? <span className="saas-badge suspended">Factura rechazada</span> : <StateBadge state={a.state}/>}
              {a.companyId && <Link className="detail-link" to={`/companies/${a.companyId}`}>Ver</Link>}
            </li>)}</ul>}
        </section>
        <section className="panel saas-panel"><div className="saas-panel-head"><h2>Pagos registrados</h2><small>{month}</small></div>
          <dl className="saas-list">
            <div><dt>Total cobrado (IVA incluido)</dt><dd>{crc.format(data.panel.monthTotal)}</dd></div>
            <div><dt>Pagos</dt><dd>{data.panel.monthPayments}</dd></div>
            <div><dt>Anulados</dt><dd>{data.panel.monthVoided}</dd></div>
            <div><dt>Facturas aceptadas por Hacienda</dt><dd>{data.invoices.accepted}</dd></div>
            <div><dt>Facturas pendientes</dt><dd>{data.invoices.pending}</dd></div>
            <div><dt>Facturas con problema</dt><dd className={data.invoices.problems ? "saas-danger" : undefined}>{data.invoices.problems}</dd></div>
          </dl>
          <p className="saas-note">Todos los planes son en colones. Cada pago genera una factura electrónica de Stockly.</p>
        </section>
      </div>
    </>}
  </section>;
}
