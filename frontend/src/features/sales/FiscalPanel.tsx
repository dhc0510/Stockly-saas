import { useState } from "react";
import { Download, FileCheck2, RefreshCw, Send } from "lucide-react";
import { failureMessage, receiptDate } from "../receipts/receiptApi";
import { request } from "../../lib/http";
import {
  ID_TYPES, currentDocument, fiscalStatusLabels, fiscalTone, fiscalTypeLabels, idProblem,
  type FiscalDocument, type FiscalStatus,
} from "./fiscal";

export function FiscalBadge({ status, prefix }: { status: FiscalStatus | "NONE"; prefix?: string }) {
  return <span className={"sales-status sales-fiscal-" + fiscalTone[status]}>{prefix ? prefix + " · " : ""}{fiscalStatusLabels[status]}</span>;
}

type Props = {
  api: string; folio: string; documents: FiscalDocument[]; voided: boolean; canWrite: boolean; hasEmail: boolean;
  customer: { name: string | null; idType: string | null; idNumber: string | null };
  onChange: (sale: unknown) => void;
};

/** STK-013 · Comprobante electrónico de la venta: estado, archivos, reintento, corrección y nota de crédito. */
export function FiscalPanel({ api, folio, documents, voided, canWrite, hasEmail, customer, onChange }: Props) {
  const [message, setMessage] = useState<{ text: string; error: boolean }>();
  const [busy, setBusy] = useState(false);
  const doc = currentDocument(documents);
  const credits = documents.filter(item => item.docType === "03" && item.referenceId === doc?.id);
  const base = api + "/" + encodeURIComponent(folio) + "/fiscal";

  async function act(action: () => Promise<void>, success: string) {
    if (busy) return;
    setBusy(true); setMessage(undefined);
    try { await action(); setMessage({ text: success, error: false }); }
    catch (failure) { setMessage({ text: failureMessage(failure), error: true }); }
    finally { setBusy(false); }
  }

  if (!doc) return <section className="sales-panel sales-work-panel" aria-label="Comprobante electrónico">
    <h2 className="sales-fiscal-title">Comprobante electrónico</h2>
    <p className="sales-payment-none" role="note">Sin comprobante. Esta venta se registró antes de habilitar la facturación electrónica.</p>
  </section>;

  const history = documents.filter(item => item.docType !== "03" && item.id !== doc.id);
  return <section className="sales-panel sales-work-panel" aria-label="Comprobante electrónico">
    <div className="sales-document-heading">
      <h2 className="sales-fiscal-title">Comprobante electrónico</h2>
      <span className="sales-fiscal-badges">{doc.environment === "TEST" && <span className="sales-status sales-fiscal-test">Pruebas</span>}
        <FiscalBadge status={doc.status} /></span>
    </div>
    {message && <p className={"notice " + (message.error ? "error" : "success")} role={message.error ? "alert" : "status"}>{message.text}</p>}
    <dl className="sales-details">
      <div><dt>Tipo</dt><dd>{fiscalTypeLabels[doc.docType]}</dd></div>
      <div><dt>Consecutivo</dt><dd className="sales-mono">{doc.consecutive}</dd></div>
      <div><dt>Sucursal Hacienda</dt><dd>{doc.branchCode}</dd></div>
      <div className="sales-details-wide"><dt>Clave numérica</dt><dd className="sales-mono">{doc.key}</dd></div>
    </dl>
    {doc.status === "ACCEPTED" && <>
      <p className="notice success"><span>Hacienda aceptó el comprobante{doc.resolvedAt ? " · " + receiptDate(doc.resolvedAt) : ""}.
        {doc.mailStatus === "SENT" ? " Se envió por correo al cliente." : doc.mailStatus === "FAILED" ? " El correo no se pudo enviar; puedes reenviarlo." : ""}</span></p>
      <Files base={base} doc={doc}>{hasEmail && <button type="button" className="button secondary" disabled={busy}
        onClick={() => act(async () => { await request(base + "/" + doc.id + "/resend", { method: "POST" }); }, "Comprobante reenviado por correo.")}>
        <Send size={16} aria-hidden="true" /> Reenviar por correo</button>}</Files>
    </>}
    {(doc.status === "PENDING" || doc.status === "PROCESSING") && <>
      <p className="notice">{doc.status === "PROCESSING" ? "Hacienda recibió el comprobante y lo está revisando. Stockly consultará el resultado."
        : doc.reason ?? "El comprobante se enviará a Hacienda en segundo plano."}
        {doc.attempts > 0 ? " Intentos: " + doc.attempts + "." : ""} Stockly consulta la clave antes de reenviar, así no se duplica.</p>
      {canWrite && <div className="sales-actions sales-actions-start"><button type="button" className="button secondary" disabled={busy}
        onClick={() => act(async () => { onChange(await request(base + "/retry", { method: "POST" })); }, "Se programó un nuevo intento.")}>
        <RefreshCw size={16} aria-hidden="true" /> Reintentar ahora</button></div>}
    </>}
    {doc.status === "CANCELLED" && <p className="notice info">{doc.reason ?? "Comprobante cancelado."}</p>}
    {doc.status === "REJECTED" && <>
      <p className="notice error"><span><strong>Motivo de Hacienda:</strong> {doc.reason ?? "Rechazado."}</span></p>
      {canWrite && !voided && <Correction customer={customer} busy={busy}
        onSubmit={body => act(async () => { onChange(await request(base + "/correct", { method: "POST", body: JSON.stringify(body) })); },
          "Comprobante nuevo enviado a Hacienda. El rechazado queda en el historial.")} />}
    </>}
    {credits.map(credit => <div key={credit.id} className="sales-credit-note">
      <div className="sales-document-heading"><h3>Nota de crédito</h3><FiscalBadge status={credit.status} /></div>
      <p>{credit.saleReturnId ? "Ajuste por devolución del comprobante" : "Anula por completo el comprobante"} {doc.consecutive}. Consecutivo <span className="sales-mono">{credit.consecutive}</span>.</p>
      {credit.reason && credit.status !== "ACCEPTED" && <p className="notice">{credit.reason}</p>}
      {credit.status === "ACCEPTED" && <Files base={base} doc={credit} />}
    </div>)}
    {history.length > 0 && <details className="sales-fiscal-history"><summary>Comprobantes anteriores ({history.length})</summary>
      <ul>{history.map(item => <li key={item.id}>{fiscalTypeLabels[item.docType]} <span className="sales-mono">{item.consecutive}</span> ·{" "}
        {fiscalStatusLabels[item.status]}{item.reason ? " · " + item.reason : ""}</li>)}</ul></details>}
  </section>;
}

function Files({ base, doc, children }: { base: string; doc: FiscalDocument; children?: React.ReactNode }) {
  const link = (file: string, label: string) => <a className="button secondary" href={base + "/" + doc.id + "/" + file} download>
    <Download size={16} aria-hidden="true" /> {label}</a>;
  return <div className="sales-actions sales-actions-start">{link("pdf", "Descargar PDF")}{link("xml", "Descargar XML")}
    {doc.status === "ACCEPTED" && link("response", "Respuesta de Hacienda")}{children}</div>;
}

function Correction({ customer, busy, onSubmit }: {
  busy: boolean; customer: Props["customer"]; onSubmit: (body: { idType: string | null; idNumber: string | null; name: string }) => void;
}) {
  const [idType, setIdType] = useState(customer.idType ?? "");
  const [idNumber, setIdNumber] = useState(customer.idNumber ?? "");
  const [name, setName] = useState(customer.name ?? "");
  const [problem, setProblem] = useState("");
  return <form className="sales-fiscal-fix" noValidate onSubmit={event => {
    event.preventDefault();
    const issue = idType ? idProblem(idType, idNumber) || (name.trim() ? "" : "La factura necesita el nombre del cliente.") : "";
    if (issue) { setProblem(issue); return; }
    onSubmit({ idType: idType || null, idNumber: idType ? idNumber.replace(/\D/g, "") : null, name: name.trim() });
  }}>
    <h3><FileCheck2 size={17} aria-hidden="true" /> Corregir y emitir de nuevo</h3>
    <p>Se emitirá un comprobante nuevo con otro consecutivo. Sin identificación se emite tiquete.</p>
    <div className="sales-contact-fields">
      <label>Tipo de identificación<select value={idType} onChange={event => { setProblem(""); setIdType(event.target.value); }}>
        <option value="">Sin identificar · emitir tiquete</option>{ID_TYPES.map(type => <option key={type.code} value={type.code}>{type.label}</option>)}
      </select></label>
      <label>Número<input inputMode="numeric" maxLength={20} value={idNumber} disabled={!idType}
        onChange={event => { setProblem(""); setIdNumber(event.target.value); }} /></label>
      <label>Nombre<input maxLength={100} value={name} onChange={event => { setProblem(""); setName(event.target.value); }} /></label>
    </div>
    {problem && <p className="sales-pay-error" role="alert">{problem}</p>}
    <div className="sales-actions"><button type="submit" className="catalog-primary" disabled={busy}>Emitir de nuevo</button></div>
  </form>;
}
