import { useEffect, useState, type FormEvent } from "react";
import { LoaderCircle } from "lucide-react";
import locations from "../billing/ubicaciones.json";
import { saasApi, type Billing, type Settings } from "./api";
import { AutoTextarea, GeneralError, errorMessage, fieldError } from "./shared";

// STK-018 · Configuración de suscripciones y facturación electrónica de Stockly.
const PROVINCES = locations.provinces;
const ID_TYPES: [string, string][] = [["02", "Cédula jurídica"], ["01", "Cédula física"], ["03", "DIMEX"], ["04", "NITE"]];

export function SaasSettingsPage() {
  const [settings, setSettings] = useState<Settings>(); const [billing, setBilling] = useState<Billing>(); const [error, setError] = useState<unknown>();
  const [reload, setReload] = useState(0); const [toast, setToast] = useState("");
  useEffect(() => { setError(undefined); Promise.all([saasApi.settings(), saasApi.billing()]).then(([s, b]) => { setSettings(s); setBilling(b); }).catch(setError); }, [reload]);
  useEffect(() => { if (!toast) return; const t = window.setTimeout(() => setToast(""), 3000); return () => window.clearTimeout(t); }, [toast]);
  return <section className="saas-page">
    <div className="page-heading"><div><p className="eyebrow">ADMINISTRACIÓN</p><h1>Configuración de suscripciones</h1>
      <p className="subtitle">Se aplica a las pruebas nuevas y a los próximos vencimientos.</p></div></div>
    {error ? <div className="state-panel saas-state error" role="alert"><h2>No pudimos cargar la configuración</h2><p>{errorMessage(error)}</p>
        <button type="button" className="button secondary" onClick={() => setReload(v => v + 1)}>Reintentar</button></div>
      : !settings || !billing ? <div className="state-panel" role="status"><LoaderCircle className="spin" size={22}/> Cargando configuración…</div> : <>
        <SubscriptionSettings value={settings} onSaved={s => { setSettings(s); setToast("Configuración guardada."); }}/>
        <BillingSettings value={billing} onSaved={(b, text) => { setBilling(b); setToast(text); }}/>
      </>}
    {toast && <div className="saas-toast" role="status">{toast}</div>}
  </section>;
}

function SubscriptionSettings({ value, onSaved }:{ value:Settings; onSaved:(s:Settings)=>void }) {
  const [form, setForm] = useState(value); const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>();
  useEffect(() => setForm(value), [value]);
  const set = (k:keyof Settings) => (e:{ target:{ value:string } }) => setForm(f => ({ ...f, [k]: k === "paymentInstructions" ? e.target.value : Number(e.target.value) }));
  async function submit(e:FormEvent) {
    e.preventDefault(); setBusy(true); setError(undefined);
    try { onSaved(await saasApi.saveSettings(form)); } catch (err) { setError(err); } finally { setBusy(false); }
  }
  const num = (k:keyof Settings, label:string, help?:string) => <label className="saas-field">{label}
    <input type="number" inputMode="numeric" value={String(form[k] ?? "")} onChange={set(k)} aria-invalid={!!fieldError(error, k)}/>{help && <small>{help}</small>}{fieldError(error, k)}</label>;
  return <form className="saas-grid" onSubmit={submit} noValidate>
    <section className="panel saas-panel"><h2>Prueba y gracia</h2>
      <div className="saas-g2">{num("trialDays", "Días de prueba")}{num("graceDays", "Días de gracia", "Acceso completo después del vencimiento.")}</div>
      <h3>Límites durante la prueba</h3>
      <div className="saas-g3">{num("trialProducts", "Productos")}{num("trialUsers", "Usuarios")}{num("trialBranches", "Sucursales")}</div>
      <p className="saas-note">La facturación electrónica funciona completa durante la prueba: el comercio emite comprobantes reales ante Hacienda.</p>
    </section>
    <section className="panel saas-panel"><h2>Avisos y forma de pago</h2>
      {num("noticeDays", "Avisar antes del vencimiento (días)", "También se avisa al vencer (inicio de la gracia) y al suspenderse.")}
      <label className="saas-field">Instrucciones de pago para el comercio
        <AutoTextarea value={form.paymentInstructions ?? ""} onChange={set("paymentInstructions")} maxLength={2000}
          placeholder="SINPE Móvil, cuenta IBAN y a dónde enviar el comprobante"/>
        <small>Se muestran en «Mi suscripción», al llegar a un límite, al suspenderse y en los correos.</small>{fieldError(error, "paymentInstructions")}</label>
      <GeneralError error={error}/>
      <div className="saas-actions"><button type="button" className="button secondary" disabled={busy} onClick={() => { setForm(value); setError(undefined); }}>Descartar</button>
        <button type="submit" className="button primary" disabled={busy}>{busy ? "Guardando…" : "Guardar configuración"}</button></div>
    </section>
  </form>;
}

function BillingSettings({ value, onSaved }:{ value:Billing; onSaved:(b:Billing, message:string)=>void }) {
  const [busy, setBusy] = useState<string>(); const [error, setError] = useState<{ section:string; error:unknown }>();
  const [place, setPlace] = useState({ province: value.province ?? "", canton: value.canton ?? "" });
  const [replacing, setReplacing] = useState(!value.hasCredentials);
  const cantons = PROVINCES.find(p => p.code === place.province)?.cantons ?? [];
  const districts = cantons.find(c => c.code === place.canton)?.districts ?? [];
  const err = (section:string) => error?.section === section ? error.error : undefined;
  async function run(section:string, action:()=>Promise<Billing>, message:string) {
    setBusy(section); setError(undefined);
    try { onSaved(await action(), message); return true; } catch (e) { setError({ section, error: e }); return false; } finally { setBusy(undefined); }
  }
  function issuer(e:FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>;
    void run("issuer", () => saasApi.saveIssuer({ legalName: f.legalName, idType: f.idType, idNumber: f.idNumber, email: f.email, activityCode: f.activityCode,
      cabysCode: f.cabysCode, province: f.province, canton: f.canton, district: f.district, otherSigns: f.otherSigns }), "Datos del emisor guardados.");
  }
  async function credentials(e:FormEvent<HTMLFormElement>) {
    e.preventDefault(); const target = e.currentTarget; const f = new FormData(target); const file = f.get("keyStore");
    if (!(file instanceof File) || !file.size) { setError({ section: "cred", error: new Error("Selecciona la llave criptográfica .p12.") }); return; }
    if (file.size > 64 * 1024) { setError({ section: "cred", error: new Error("La llave no puede pesar más de 64 KB.") }); return; }
    let binary = ""; new Uint8Array(await file.arrayBuffer()).forEach(b => { binary += String.fromCharCode(b); });
    const ok = await run("cred", () => saasApi.saveCredentials({ username: String(f.get("username") ?? ""), password: String(f.get("password") ?? ""),
      pin: String(f.get("pin") ?? ""), keyStore: btoa(binary) }), "Credenciales guardadas y conexión probada con Hacienda.");
    if (ok) { target.reset(); setReplacing(false); }
  }
  const expires = value.keyExpiresAt ? new Intl.DateTimeFormat("es-CR", { dateStyle: "medium", timeZone: "America/Costa_Rica" }).format(new Date(value.keyExpiresAt)) : null;
  return <section className="panel saas-panel saas-billing">
    <div className="saas-panel-head"><h2>Facturación electrónica de Stockly</h2>
      <span className={`saas-badge ${value.hasCredentials && value.credentialsCheckedAt ? "active" : "off"}`}>{value.hasCredentials && value.credentialsCheckedAt ? "Credenciales verificadas" : "Sin credenciales"}</span></div>
    <p className="saas-muted">Datos del emisor para facturar los pagos de suscripción. Mismo motor de firma y envío que la facturación de los comercios.
      Ambiente: <strong>{value.environment === "PROD" ? "Producción" : "Pruebas de Hacienda"}</strong>.</p>
    {!value.serverReady && <div className="saas-callout">Este servidor no tiene habilitada la facturación electrónica (clave de cifrado o cédula del proveedor de sistemas).</div>}
    <form onSubmit={issuer} noValidate>
      <div className="saas-g3">
        <label className="saas-field">Razón social<input name="legalName" defaultValue={value.legalName ?? ""} maxLength={100}/>{fieldError(err("issuer"), "legalName")}</label>
        <label className="saas-field">Tipo de identificación<select name="idType" defaultValue={value.idType ?? "02"}>{ID_TYPES.map(([c, l]) => <option key={c} value={c}>{l}</option>)}</select>{fieldError(err("issuer"), "idType")}</label>
        <label className="saas-field">Número de identificación<input name="idNumber" defaultValue={value.idNumber ?? ""} inputMode="numeric"/>{fieldError(err("issuer"), "idNumber")}</label>
        <label className="saas-field">Correo para facturas<input name="email" type="email" defaultValue={value.email ?? ""}/>{fieldError(err("issuer"), "email")}</label>
        <label className="saas-field">Actividad económica<input name="activityCode" defaultValue={value.activityCode ?? ""} inputMode="numeric" placeholder="6 dígitos"/>{fieldError(err("issuer"), "activityCode")}</label>
        <label className="saas-field">Código CABYS del servicio<input name="cabysCode" defaultValue={value.cabysCode ?? ""} inputMode="numeric" placeholder="13 dígitos"/>
          <small>Lo define Stockly con su contador.</small>{fieldError(err("issuer"), "cabysCode")}</label>
        <label className="saas-field">Provincia<select name="province" value={place.province} onChange={e => setPlace({ province: e.target.value, canton: "" })}>
          <option value="">Selecciona</option>{PROVINCES.map(p => <option key={p.code} value={p.code}>{p.name}</option>)}</select>{fieldError(err("issuer"), "province")}</label>
        <label className="saas-field">Cantón<select name="canton" value={place.canton} disabled={!cantons.length} onChange={e => setPlace(p => ({ ...p, canton: e.target.value }))}>
          <option value="">Selecciona</option>{cantons.map(c => <option key={c.code} value={c.code}>{c.name}</option>)}</select>{fieldError(err("issuer"), "canton")}</label>
        <label className="saas-field">Distrito<select name="district" key={place.province + place.canton} defaultValue={place.canton === value.canton ? value.district ?? "" : ""} disabled={!districts.length}>
          <option value="">Selecciona</option>{districts.map(d => <option key={d.code} value={d.code}>{d.name}</option>)}</select>{fieldError(err("issuer"), "district")}</label>
      </div>
      <label className="saas-field">Otras señas<AutoTextarea name="otherSigns" defaultValue={value.otherSigns ?? ""} maxLength={250} rows={2}/>{fieldError(err("issuer"), "otherSigns")}</label>
      <GeneralError error={err("issuer")}/>
      <div className="saas-actions"><button type="submit" className="button primary" disabled={!!busy}>{busy === "issuer" ? "Guardando…" : "Guardar datos del emisor"}</button></div>
    </form>
    <h3>Credenciales de Hacienda</h3>
    {value.hasCredentials && !replacing ? <div className="saas-credentials">
        <p>Llave cargada{expires ? <> · vence el {expires}</> : null}. Las credenciales se guardan cifradas y nunca se muestran.</p>
        <div className="saas-actions">
          <button type="button" className="button secondary" disabled={!!busy} onClick={() => void run("test", saasApi.testBilling, "Conexión con Hacienda verificada.")}>{busy === "test" ? "Probando…" : "Probar conexión"}</button>
          <button type="button" className="button secondary" disabled={!!busy} onClick={() => setReplacing(true)}>Reemplazar credenciales</button>
          {value.environment === "TEST"
            ? <button type="button" className="button secondary" disabled={!!busy || !value.productionReady} title={value.productionReady ? undefined : "El servidor no tiene configurado el ambiente de producción."}
                onClick={() => void run("env", () => saasApi.environment("PROD"), "Facturación de Stockly en producción.")}>Activar producción</button>
            : <button type="button" className="button secondary" disabled={!!busy} onClick={() => void run("env", () => saasApi.environment("TEST"), "Facturación de Stockly en pruebas.")}>Volver a pruebas</button>}
        </div>
        <GeneralError error={err("test") ?? err("env")}/>
      </div>
      : <form onSubmit={e => void credentials(e)} noValidate autoComplete="off">
        <div className="saas-g2">
          <label className="saas-field">Usuario de API<input name="username" autoComplete="off"/></label>
          <label className="saas-field">Contraseña de API<input name="password" type="password" autoComplete="new-password"/></label>
          <label className="saas-field">Llave criptográfica (.p12)<input name="keyStore" type="file" accept=".p12,application/x-pkcs12"/></label>
          <label className="saas-field">PIN de la llave<input name="pin" type="password" inputMode="numeric" autoComplete="off"/></label>
        </div>
        <p className="saas-note">Se cifran al guardarse y se prueban con Hacienda. Si faltan o fallan, los pagos se registran igual y sus facturas quedan pendientes.</p>
        <GeneralError error={err("cred")}/>
        <div className="saas-actions">{value.hasCredentials && <button type="button" className="button secondary" disabled={!!busy} onClick={() => setReplacing(false)}>Cancelar</button>}
          <button type="submit" className="button primary" disabled={!!busy || !value.serverReady}>{busy === "cred" ? "Probando con Hacienda…" : "Guardar y probar"}</button></div>
      </form>}
  </section>;
}
