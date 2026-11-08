import { useEffect, useState, type FormEvent } from "react";
import { Building2, CheckCircle2, Circle, KeyRound, MapPin, ShieldCheck } from "lucide-react";
import { failureMessage, receiptDate } from "../receipts/receiptApi";
import { ApiError, request } from "../../lib/http";
import { Heading, PanelHeading } from "../sales/SalesPage";
import "../receipts/receipts.css";
import "../sales/sales.css";
import "./billing.css";
import locations from "./ubicaciones.json";

const api = "/api/billing/settings";
// Codificación oficial de ubicación (Anexos v4.4): provincia, cantón y distrito.
const PROVINCES = locations.provinces;
const DOC_TYPES: [string, string][] = [["01", "Factura"], ["04", "Tiquete"], ["03", "Nota de crédito"]];

type Settings = {
  environment: "TEST" | "PROD"; billingEnabled: boolean; hasCredentials: boolean; keySubject: string | null; keyExpiresAt: string | null;
  credentialsCheckedAt: string | null; issuer: { name: string; idType: string; idNumber: string }; activityCode: string | null;
  province: string | null; canton: string | null; district: string | null; otherSigns: string | null; email: string | null;
  productionSince: string | null;
  branches: { id: string; name: string; fiscalCode: string | null; active: boolean; sequences: { docType: string; lastNumber: number }[] }[];
  requirements: { serverReady: boolean; credentials: boolean; issuer: boolean; testAccepted: boolean; productionReady: boolean };
};

/** STK-013 · Facturación electrónica: credenciales de Hacienda, emisor, sucursales y ambiente. Solo administradores. */
export function BillingPage() {
  const [settings, setSettings] = useState<Settings>();
  // El resultado de cada acción se muestra dentro de su sección, junto al botón que la disparó.
  const [message, setMessage] = useState<{ text: string; error: boolean; section?: string }>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [replacing, setReplacing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [place, setPlace] = useState<{ province: string; canton: string }>();
  const [sequenceError, setSequenceError] = useState<{ branch: string; text: string }>();
  // Los avisos de éxito se cierran solos; los de error quedan hasta cerrarlos.
  useEffect(() => {
    if (!message?.section || message.error) return;
    const timer = window.setTimeout(() => setMessage(undefined), 4000);
    return () => window.clearTimeout(timer);
  }, [message]);
  useEffect(() => {
    const controller = new AbortController();
    request<Settings>(api, { signal: controller.signal }).then(setSettings)
      .catch(failure => { if (!controller.signal.aborted) setMessage({ text: failureMessage(failure), error: true }); });
    return () => controller.abort();
  }, []);

  async function run(action: () => Promise<Settings>, success: string, section: string) {
    if (busy) return;
    // El aviso anterior se reemplaza al terminar: quitarlo antes hacía saltar la pantalla.
    setBusy(true); setFields({});
    try { setSettings(await action()); setMessage({ text: success, error: false, section }); return true; }
    catch (failure) {
      setMessage({ text: failureMessage(failure), error: true, section });
      if (failure instanceof ApiError) setFields(failure.fieldErrors);
      return false;
    } finally { setBusy(false); }
  }

  async function saveCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // El formulario se toma antes de cualquier espera: después React ya no expone currentTarget.
    const target = event.currentTarget;
    const form = new FormData(target);
    const file = form.get("keyStore");
    if (!(file instanceof File) || !file.size) { setMessage({ text: "Selecciona la llave criptográfica .p12.", error: true, section: "cred" }); return; }
    if (file.size > 64 * 1024) { setMessage({ text: "La llave no puede pesar más de 64 KB.", error: true, section: "cred" }); return; }
    const bytes = new Uint8Array(await file.arrayBuffer());
    let binary = "";
    bytes.forEach(byte => { binary += String.fromCharCode(byte); });
    const ok = await run(() => request<Settings>(api + "/credentials", { method: "PUT", body: JSON.stringify({
      username: form.get("username"), password: form.get("password"), pin: form.get("pin"), keyStore: btoa(binary),
    }) }), "Credenciales guardadas y conexión probada con Hacienda.", "cred");
    if (ok) { target.reset(); setReplacing(false); }
  }

  async function saveIssuer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    await run(() => request<Settings>(api + "/issuer", { method: "PUT", body: JSON.stringify(form) }), "Datos del emisor guardados.", "issuer");
  }

  if (!settings) return <div className="catalog-page sales-page">
    <Heading title="Facturación electrónica" description="Credenciales de Hacienda, datos del emisor y sucursales." />
    {message ? <p className="notice error" role="alert">{message.text}</p> : <p role="status">Cargando configuración…</p>}
  </div>;

  const { requirements: req } = settings;
  const province = place?.province ?? settings.province ?? "";
  const canton = place ? place.canton : settings.canton ?? "";
  const cantons = PROVINCES.find(item => item.code === province)?.cantons ?? [];
  const districts = cantons.find(item => item.code === canton)?.districts ?? [];
  const ready = req.serverReady && req.credentials && req.issuer && req.testAccepted && req.productionReady;
  const step = (done: boolean, text: string) => <li className={done ? "billing-done" : undefined}>
    {done ? <CheckCircle2 size={17} aria-hidden="true" /> : <Circle size={17} aria-hidden="true" />}<span>{text}</span>
    <span className="visually-hidden">{done ? " (listo)" : " (pendiente)"}</span></li>;
  // Los resultados de las acciones se muestran en un aviso flotante que no desplaza el contenido.
  const notice = (section?: string) => section === undefined && message && !message.section &&
    <p className={"notice " + (message.error ? "error" : "success")} role={message.error ? "alert" : "status"}>{message.text}</p>;
  const error = (name: string) => fields[name] && <small className="billing-field-error" role="alert">{fields[name]}</small>;

  return <div className="catalog-page sales-page billing-page">
    <Heading title="Facturación electrónica" description="Credenciales de Hacienda, datos del emisor y sucursales. Solo administradores." />
    {notice()}
    {message?.section && <div className={"billing-toast " + (message.error ? "billing-toast-error" : "")} role={message.error ? "alert" : "status"}>
      <span>{message.text}</span>
      <button type="button" className="billing-toast-close" aria-label="Cerrar aviso" onClick={() => setMessage(undefined)}>×</button>
    </div>}
    {!req.serverReady && <p className="notice error" role="alert">La facturación no está habilitada en este servidor. El equipo de Stockly debe configurar la clave de cifrado y la cédula del proveedor de sistemas.</p>}

    <section className="sales-panel sales-work-panel" aria-label="Ambiente">
      <div className="sales-document-heading"><PanelHeading icon={ShieldCheck} title="Ambiente"
        description={settings.environment === "TEST" ? "Los comprobantes van al ambiente de pruebas de Hacienda y no tienen validez fiscal." : "Los comprobantes tienen validez fiscal."} />
        <span className={"sales-status " + (settings.environment === "TEST" ? "sales-fiscal-test" : "sales-fiscal-ok")}>{settings.environment === "TEST" ? "Pruebas" : "Producción"}</span></div>
      {notice("env")}
      {settings.environment === "TEST" ? <>
        <ol className="billing-steps">
          {step(req.credentials, "Credenciales guardadas y conexión probada")}
          {step(req.issuer, "Datos del emisor completos")}
          {step(req.testAccepted, "Al menos un comprobante de prueba aceptado por Hacienda")}
          {step(req.productionReady, "Servidor de Stockly configurado para el ambiente de producción de Hacienda")}
        </ol>
        {confirming ? <div className="billing-confirm" role="group" aria-label="Confirmar producción">
          <p>Desde ahora los comprobantes tendrán validez fiscal. Confirma con tu contador que el emisor y los códigos CABYS están correctos. No se puede volver a pruebas desde la aplicación.</p>
          <div className="sales-actions"><button type="button" className="button secondary" onClick={() => setConfirming(false)}>Volver</button>
            <button type="button" className="catalog-primary" disabled={busy} onClick={async () => {
              if (await run(() => request<Settings>(api + "/production", { method: "POST" }), "Producción activada.", "env")) setConfirming(false);
            }}>Activar producción</button></div>
        </div> : <div className="sales-actions"><button type="button" className="catalog-primary" disabled={!ready} onClick={() => setConfirming(true)}>Activar producción</button></div>}
      </> : <p className="sales-summary-note">Activo desde {settings.productionSince ? receiptDate(settings.productionSince) : "—"}.</p>}
    </section>

    <section className="sales-panel sales-work-panel" aria-label="Credenciales de Hacienda">
      <PanelHeading icon={KeyRound} title="Credenciales de Hacienda" description="Se obtienen en TRIBU-CR. Stockly las guarda cifradas y nunca las vuelve a mostrar." />
      {notice("cred")}
      {settings.hasCredentials && !replacing && <>
        <div className="billing-secret"><div><strong>Usuario y contraseña de API</strong><small>Guardados</small></div><span className="sales-status sales-fiscal-ok">Guardado</span></div>
        <div className="billing-secret"><div><strong>Llave criptográfica</strong><small>{settings.keySubject}
          {settings.keyExpiresAt ? " · vence el " + new Date(settings.keyExpiresAt).toLocaleDateString("es-CR") : ""}</small></div>
          <span className="sales-status sales-fiscal-ok">Guardada</span></div>
        <div className="sales-actions">
          <button type="button" className="button secondary" disabled={busy}
            onClick={() => run(() => request<Settings>(api + "/test", { method: "POST" }), "Conexión correcta con Hacienda.", "cred")}>{busy ? "Probando…" : "Probar conexión"}</button>
          <button type="button" className="button secondary" onClick={() => setReplacing(true)}>Reemplazar credenciales</button>
        </div>
      </>}
      {(!settings.hasCredentials || replacing) && <form className="billing-grid" onSubmit={saveCredentials} noValidate>
        <label>Usuario de API<input name="username" autoComplete="off" required maxLength={120} /></label>
        <label>Contraseña de API<input name="password" type="password" autoComplete="new-password" required maxLength={200} /></label>
        <label>Llave criptográfica (.p12)<input name="keyStore" type="file" accept=".p12,application/x-pkcs12" required /></label>
        <label>PIN de la llave<input name="pin" type="password" inputMode="numeric" autoComplete="new-password" required maxLength={20} /></label>
        <div className="sales-actions billing-span">
          {replacing && <button type="button" className="button secondary" onClick={() => setReplacing(false)}>Cancelar</button>}
          <button type="submit" className="catalog-primary" disabled={busy || !req.serverReady}>{busy ? "Probando…" : "Guardar y probar"}</button>
        </div>
      </form>}
    </section>

    <section className="sales-panel sales-work-panel" aria-label="Datos del emisor">
      <PanelHeading icon={MapPin} title="Datos del emisor" description="Se envían en cada comprobante. La ubicación sigue la codificación oficial de Hacienda." />
      {notice("issuer")}
      <form className="billing-grid" onSubmit={saveIssuer} noValidate>
        <label>Nombre y cédula<input value={settings.issuer.name + " · " + settings.issuer.idNumber} disabled /><small>Vienen del registro de la empresa.</small></label>
        <label>Correo para comprobantes<input name="email" type="email" defaultValue={settings.email ?? ""} maxLength={160} aria-invalid={!!fields.email} />{error("email")}</label>
        <label className="billing-span">Actividad económica (código de 6 dígitos)<input name="activityCode" inputMode="numeric" maxLength={6}
          defaultValue={settings.activityCode ?? ""} aria-invalid={!!fields.activityCode} />{error("activityCode")}<small>El código inscrito en Hacienda para esta actividad.</small></label>
        <label>Provincia<select name="province" value={province} aria-invalid={!!fields.province}
          onChange={event => setPlace({ province: event.target.value, canton: "" })}>
          <option value="">Selecciona</option>{PROVINCES.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}</select>{error("province")}</label>
        <label>Cantón<select name="canton" value={canton} disabled={!cantons.length} aria-invalid={!!fields.canton}
          onChange={event => setPlace({ province, canton: event.target.value })}>
          <option value="">Selecciona</option>{cantons.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}</select>{error("canton")}</label>
        <label>Distrito<select name="district" key={province + canton} defaultValue={!place ? settings.district ?? "" : ""} disabled={!districts.length} aria-invalid={!!fields.district}>
          <option value="">Selecciona</option>{districts.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}</select>{error("district")}</label>
        <label>Otras señas<input name="otherSigns" maxLength={250} defaultValue={settings.otherSigns ?? ""} aria-invalid={!!fields.otherSigns} />{error("otherSigns")}</label>
        <div className="sales-actions billing-span"><button type="submit" className="catalog-primary" disabled={busy}>Guardar</button></div>
      </form>
    </section>

    <section className="sales-panel sales-work-panel" aria-label="Sucursales en Hacienda">
      <PanelHeading icon={Building2} title="Sucursales en Hacienda" description="Cada sucursal emite con su propio código y consecutivos. El punto de venta es 00001." />
      {notice("branches")}
      <ul className="billing-branches">{settings.branches.map(branch => <li key={branch.id}>
        <div><strong>{branch.name}</strong>{!branch.active && <small> · Inactiva</small>}
          <small>Código {branch.fiscalCode ?? "se asigna al emitir"} · Punto de venta 00001</small></div>
        <span className="billing-sequences"><span>Última emitida:</span>{DOC_TYPES.map(([code, label]) =>
          <span key={code}>{label} <strong>{branch.sequences.find(item => item.docType === code)?.lastNumber ?? 0}</strong></span>)}</span>
        <details className="billing-span billing-continue"><summary>¿Ya facturabas con otro sistema? Continúa tu numeración</summary>
          <ContinueNumbering branchCode={branch.fiscalCode ?? "001"} busy={busy} error={sequenceError?.branch === branch.id ? sequenceError.text : ""}
            onError={text => setSequenceError(text ? { branch: branch.id, text } : undefined)}
            onSave={(docType, lastUsed) => void run(() => request<Settings>(api + "/branches/" + branch.id + "/sequences/" + docType, {
              method: "PUT", body: JSON.stringify({ lastUsed }) }), "Numeración guardada. Stockly seguirá desde el número siguiente.", "branches")} />
        </details>
      </li>)}</ul>
      <p className="sales-summary-note">Hacienda exige que, si ya emitías comprobantes con otro sistema, Stockly continúe tu numeración en lugar de empezar en 1. Solo se puede indicar antes de emitir el primer comprobante de ese tipo en Stockly.</p>
    </section>
  </div>;
}

const DOC_NAMES: Record<string, string> = { "01": "factura", "04": "tiquete", "03": "nota de crédito" };

/** Nota 3: continuar la numeración del sistema anterior. Muestra un ejemplo y cuál será el siguiente comprobante. */
function ContinueNumbering({ branchCode, busy, error, onError, onSave }: {
  branchCode: string; busy: boolean; error: string; onError: (text: string) => void; onSave: (docType: string, lastUsed: number) => void;
}) {
  const [docType, setDocType] = useState("01");
  const [value, setValue] = useState("");
  const raw = value.trim();
  const valid = /^\d{1,10}$/.test(raw) && Number(raw) <= 9999999998;
  const next = valid ? Number(raw) + 1 : null;
  const name = DOC_NAMES[docType];
  return <form className="billing-continue-form" noValidate onSubmit={event => {
    event.preventDefault();
    if (!valid) { onError("Número inválido. Escribe solo el número de tu última " + name + ", por ejemplo 245."); return; }
    onSave(docType, Number(raw));
  }}>
    <p className="billing-help">Escribe el número de la <strong>última {name}</strong> que emitiste en tu sistema anterior en esta sucursal: solo los
      últimos dígitos del consecutivo. Por ejemplo, si tu última {name} fue <span className="sales-mono">{branchCode}00001{docType}0000000245</span>, escribe <strong>245</strong>.</p>
    <div className="billing-continue-fields">
      <label>Tipo de comprobante<select value={docType} onChange={event => { setDocType(event.target.value); onError(""); }}>
        {DOC_TYPES.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
      <label>Número de tu última {name}<input inputMode="numeric" autoComplete="off" placeholder="Ej. 245" value={value}
        aria-invalid={!!error} onChange={event => { setValue(event.target.value); onError(""); }} /></label>
      <button type="submit" className="button secondary" disabled={busy}>Guardar</button>
    </div>
    {error ? <small className="billing-field-error" role="alert">{error}</small>
      : next !== null && <small className="billing-next">La próxima {name} de Stockly en esta sucursal será la <strong>{next}</strong>.</small>}
  </form>;
}
