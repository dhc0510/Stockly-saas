import { useEffect, useRef, useState, type FormEvent } from "react";
import { LoaderCircle, Plus } from "lucide-react";
import { crc, saasApi, type Plan } from "./api";
import { AutoTextarea, Dialog, GeneralError, errorMessage, fieldError } from "./shared";

// STK-018 · Planes en colones con IVA incluido. Un plan con historial se archiva, no se borra.
export function PlansPage() {
  const [plans, setPlans] = useState<Plan[]>(); const [error, setError] = useState<unknown>(); const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<Plan | "new">(); const [toast, setToast] = useState("");
  useEffect(() => { setError(undefined); saasApi.plans().then(setPlans).catch(setError); }, [reload]);
  useEffect(() => { if (!toast) return; const t = window.setTimeout(() => setToast(""), 3000); return () => window.clearTimeout(t); }, [toast]);
  const saving = (p:Plan) => p.annualPrice ? Math.round((1 - p.annualPrice / (p.monthlyPrice * 12)) * 100) : null;
  const badge = (p:Plan) => p.active ? <span className="saas-badge active">Disponible</span> : <span className="saas-badge off">Archivado</span>;
  return <section className="saas-page">
    <div className="page-heading"><div><p className="eyebrow">ADMINISTRACIÓN</p><h1>Planes</h1>
      <p className="subtitle">Precios en colones con IVA incluido. Los planes pagados dan acceso completo, sin límites. Cambiar un precio aplica a los próximos pagos; lo ya pagado no cambia.</p></div>
      <button type="button" className="button primary" onClick={() => setEditing("new")}><Plus size={18}/> Crear plan</button></div>
    {error ? <div className="state-panel saas-state error" role="alert"><h2>No pudimos cargar los planes</h2><p>{errorMessage(error)}</p>
        <button type="button" className="button secondary" onClick={() => setReload(v => v + 1)}>Reintentar</button></div>
      : !plans ? <div className="state-panel" role="status"><LoaderCircle className="spin" size={22}/> Cargando planes…</div>
      : plans.length === 0 ? <div className="state-panel saas-state" role="status"><h2>Todavía no hay planes</h2><p>Crea el primero para poder registrar pagos.</p></div>
      : <div className="saas-table-wrap">
        <table className="saas-table saas-wide"><thead><tr><th>Plan</th><th className="num">Mensual</th><th className="num">Anual</th><th className="num">Empresas</th><th>Estado</th><th><span className="sr-only">Acciones</span></th></tr></thead>
          <tbody>{plans.map(p => <tr key={p.id}><td><strong>{p.name}</strong>{p.description && <small>{p.description}</small>}</td>
            <td className="num">{crc.format(p.monthlyPrice)}</td>
            <td className="num">{p.annualPrice ? <>{crc.format(p.annualPrice)}{saving(p) ? <small>{saving(p)} % de ahorro</small> : null}</> : <small>Sin plan anual</small>}</td>
            <td className="num">{p.companies}</td><td>{badge(p)}</td>
            <td><button type="button" className="inventory-text-button" onClick={() => setEditing(p)} aria-label={`Editar ${p.name}`}>Editar</button></td></tr>)}</tbody></table>
        <div className="saas-cards">{plans.map(p => <article key={p.id} className="saas-card"><div className="saas-card-head"><strong>{p.name}</strong>{badge(p)}</div>
          <dl><div><dt>Mensual</dt><dd>{crc.format(p.monthlyPrice)}</dd></div><div><dt>Anual</dt><dd>{p.annualPrice ? crc.format(p.annualPrice) : "—"}</dd></div></dl>
          <div className="saas-card-foot"><small>{p.companies === 1 ? "1 empresa" : `${p.companies} empresas`}</small><button type="button" className="inventory-text-button" onClick={() => setEditing(p)}>Editar</button></div></article>)}</div>
      </div>}
    <p className="saas-note">Un plan archivado no se ofrece para empezar; quienes ya lo tienen pueden renovarlo.</p>
    {editing && <PlanDialog plan={editing === "new" ? undefined : editing} onClose={() => setEditing(undefined)}
      onSaved={p => { setEditing(undefined); setToast(`Plan «${p.name}» guardado.`); setReload(v => v + 1); }}/>}
    {toast && <div className="saas-toast" role="status">{toast}</div>}
  </section>;
}

function PlanDialog({ plan, onClose, onSaved }:{ plan?:Plan; onClose:()=>void; onSaved:(p:Plan)=>void }) {
  const [name, setName] = useState(plan?.name ?? ""); const [monthly, setMonthly] = useState(plan ? String(plan.monthlyPrice) : "");
  const [annual, setAnnual] = useState(plan?.annualPrice ? String(plan.annualPrice) : ""); const [description, setDescription] = useState(plan?.description ?? "");
  const [active, setActive] = useState(plan?.active ?? true); const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>();
  const guard = useRef(false);
  async function submit(e:FormEvent) {
    e.preventDefault(); if (guard.current) return; guard.current = true; setBusy(true); setError(undefined);
    try { onSaved(await saasApi.savePlan(plan?.id, { name, monthlyPrice: monthly ? Number(monthly) : null, annualPrice: annual ? Number(annual) : null,
      description, active, version: plan?.version })); }
    catch (err) { setError(err); } finally { guard.current = false; setBusy(false); }
  }
  return <Dialog title={plan ? "Editar plan" : "Crear plan"} busy={busy} onClose={onClose} onSubmit={submit}
    actions={<><button type="button" className="button secondary" onClick={onClose} disabled={busy}>Cancelar</button>
      <button type="submit" className="button primary" disabled={busy}>{busy ? "Guardando…" : "Guardar plan"}</button></>}>
    <label className="saas-field">Nombre<input value={name} onChange={e => setName(e.target.value)} maxLength={80} aria-invalid={!!fieldError(error, "name")}/>{fieldError(error, "name")}</label>
    <div className="saas-g2">
      <label className="saas-field">Precio mensual (₡, IVA incluido)<input inputMode="decimal" value={monthly} onChange={e => setMonthly(e.target.value)} aria-invalid={!!fieldError(error, "monthlyPrice")}/>{fieldError(error, "monthlyPrice")}</label>
      <label className="saas-field">Precio anual (₡, IVA incluido)<input inputMode="decimal" value={annual} onChange={e => setAnnual(e.target.value)} aria-invalid={!!fieldError(error, "annualPrice")}/>
        <small>Opcional. Vacío = sin plan anual.</small>{fieldError(error, "annualPrice")}</label>
    </div>
    <label className="saas-field">Descripción para el comercio<AutoTextarea value={description} onChange={e => setDescription(e.target.value)} maxLength={500}/>{fieldError(error, "description")}</label>
    <label className="inventory-check saas-check"><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)}/><span>Disponible para nuevas suscripciones</span></label>
    {plan && plan.companies > 0 && <div className="saas-callout info">{plan.companies === 1 ? "1 empresa usa" : `${plan.companies} empresas usan`} este plan. El nuevo precio aplica desde su próximo pago.</div>}
    <GeneralError error={error}/>
  </Dialog>;
}
