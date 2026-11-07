import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import type { SessionUser } from "../auth/api";
import { amount, failureMessage, receiptDate } from "../receipts/receiptApi";
import "../receipts/receipts.css";
import "./movements.css";
import { Difference, MovementEditor, movementDraftStatus } from "./MovementEditor";
import { movementApi, round, typeLabels, type Movement, type MovementDocument, type MovementType } from "./movementApi";

export function movementAccess(user: SessionUser) {
  const admin = user.role === "COMPANY_ADMIN";
  const level = user.modules.find(module => module.moduleKey === "INVENTORY_MOVEMENTS")?.accessLevel;
  const read = admin || (!!level && level !== "NONE");
  const write = admin || level === "OPERATE" || level === "ADMIN";
  return {
    read, write,
    adjust: admin || (write && user.specialPermissions.includes("ADJUST_INVENTORY")),
    reverse: admin || (read && user.specialPermissions.includes("VOID_INVENTORY_MOVEMENTS")),
  };
}
type Access = ReturnType<typeof movementAccess>;
const base = "/inventory/movements";
const paths: Record<MovementType, string> = { OUT: "new-outflow", ADJUST: "new-adjustment" };

function Status({ entry }: { entry: Movement }) {
  return <span className={entry.voidId ? "catalog-badge" : "catalog-badge active"}>
    {entry.voidId ? "Anulado" : entry.kind === "VOID" ? "Movimiento inverso" : "Confirmado"}
  </span>;
}

export function MovementsPage({ user, access }: { user: SessionUser; access: Access }) {
  const [search, setSearch] = useState("");
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [entries, setEntries] = useState<Movement[]>([]);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  const [discard, setDiscard] = useState<MovementType>();
  const { "*": segment = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const storageKey = (kind: MovementType) => "stockly:movement:" + kind + ":" + user.companyId + ":" + user.id;
  const editing = (Object.keys(paths) as MovementType[]).find(kind => paths[kind] === segment);
  const detailId = segment && !editing ? segment : "";
  const allowed: Record<MovementType, boolean> = { OUT: access.write, ADJUST: access.adjust };
  const drafts = segment ? [] : (["OUT", "ADJUST"] as MovementType[]).filter(kind => allowed[kind])
    .map(kind => ({ kind, state: movementDraftStatus(storageKey(kind)) })).filter(draft => draft.state);
  const returnToList = () => navigate(base);
  const open = (id: string) => navigate(base + "/" + encodeURIComponent(id));

  useEffect(() => {
    if (segment) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const timer = window.setTimeout(() => {
      movementApi.list(search, type, status, page, controller.signal).then(result => {
        if (!controller.signal.aborted) { setEntries(result.entries); setMore(result.hasMore); }
      }).catch(failure => {
        if (!controller.signal.aborted) setError(failureMessage(failure));
      }).finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    }, 150);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [search, type, status, page, version, segment]);

  if (editing) {
    if (!allowed[editing]) return <Navigate to={base} replace />;
    return <MovementEditor key={editing} type={editing} storageKey={storageKey(editing)} close={returnToList}
      saved={result => navigate(base + "/" + result.movement.id, { replace: true, state: { confirmed: true } })} />;
  }

  if (detailId) return <MovementRoute key={detailId} id={detailId} canReverse={access.reverse}
    confirmed={location.state?.confirmed === true} open={open} back={returnToList} />;

  function discardDraft(kind: MovementType) {
    try { localStorage.removeItem(storageKey(kind)); setDiscard(undefined); setVersion(value => value + 1); }
    catch { setError("El navegador no pudo descartar el borrador."); }
  }

  const filtered = !!(search.trim() || type || status);
  return <section className="catalog-page receipts-page">
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Salidas y ajustes</p>
      <h1>Salidas y ajustes</h1><p className="subtitle">Registra lo que sale del inventario y corrige el saldo tras un conteo.</p></div>
      {(access.write || access.adjust) && <div className="catalog-actions movement-new">
        {access.write && <button className="catalog-primary" onClick={() => navigate(base + "/" + paths.OUT)}><Plus size={18} /> Nueva salida</button>}
        {access.adjust && <button onClick={() => navigate(base + "/" + paths.ADJUST)}><Plus size={18} /> Nuevo ajuste</button>}
      </div>}
    </div>
    {drafts.map(({ kind, state }) => {
      const noun = kind === "ADJUST" ? "un ajuste" : "una salida";
      return <div className="receipt-note receipt-draft-notice" key={kind}>
        <strong>Tienes {noun} sin confirmar</strong>
        {state === "pending" && <p>Hay una confirmación pendiente. Continúa para comprobar el resultado.</p>}
        {discard === kind && <p>Se borrará lo guardado en este navegador.</p>}
        <div className="catalog-actions">
          {discard === kind ? <>
            <button onClick={() => setDiscard(undefined)}>Cancelar</button>
            <button className="receipt-danger" onClick={() => discardDraft(kind)}>Confirmar descarte</button>
          </> : <>
            <button className="catalog-primary" onClick={() => navigate(base + "/" + paths[kind])}>Continuar</button>
            {state !== "pending" && <button onClick={() => setDiscard(kind)}>Descartar</button>}
          </>}
        </div>
      </div>;
    })}
    <div className="catalog-panel">
      <div className="receipt-filters movement-filters">
        <label>Buscar<span className="catalog-search"><Search size={16} aria-hidden="true" />
          <input type="search" placeholder="Folio o referencia" maxLength={160} value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} />
        </span></label>
        <label>Tipo<select value={type} onChange={event => { setType(event.target.value); setPage(0); }}>
          <option value="">Todos</option><option value="OUT">Salidas</option><option value="ADJUST">Ajustes</option>
        </select></label>
        <label>Estado<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">Todos</option><option value="confirmed">Confirmados</option><option value="voided">Anulados</option>
        </select></label>
      </div>
      {error && <p className="notice error" role="alert">{error} <button onClick={() => setVersion(value => value + 1)}>Reintentar</button></p>}
      {loading ? <p className="catalog-state" role="status">Cargando movimientos…</p> :
        error ? null :
        !entries.length ? <p className="catalog-state">{filtered ? "Sin movimientos con estos filtros." : "Todavía no hay salidas ni ajustes."}</p> :
        <table className="receipt-table receipt-list"><thead><tr><th>Folio</th><th>Tipo</th><th>Fecha</th><th>Motivo</th><th>Estado</th></tr></thead>
          <tbody>{entries.map(entry => <tr key={entry.id}>
            <td><Link className="receipt-link" to={base + "/" + entry.id}>{entry.folio}</Link></td>
            <td><span className="receipt-mobile-label">Tipo</span>{typeLabels[entry.kind as MovementType]}</td>
            <td className="receipt-date"><time dateTime={entry.date}>{receiptDate(entry.date)}</time></td>
            <td>{entry.reason}{entry.reference && <small>{entry.reference}</small>}</td>
            <td><Status entry={entry} /></td>
          </tr>)}</tbody></table>}
      {!loading && !error && (page > 0 || more) && <div className="catalog-pagination">
        <button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button>
        <span>Página {page + 1}</span><button disabled={!more} onClick={() => setPage(value => value + 1)}>Siguiente</button>
      </div>}
    </div>
  </section>;
}

function MovementRoute({ id, canReverse, confirmed, open, back }: {
  id: string; canReverse: boolean; confirmed: boolean; open: (id: string) => void; back: () => void;
}) {
  const [document, setDocument] = useState<MovementDocument>();
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    movementApi.get(id, controller.signal).then(result => {
      if (controller.signal.aborted) return;
      setDocument(result);
      if (confirmed) setNotice(result.movement.kind === "ADJUST" ? "Ajuste confirmado." : "Salida confirmada.");
    }).catch(failure => {
      if (!controller.signal.aborted) setError(failureMessage(failure));
    });
    return () => controller.abort();
  }, [id, confirmed]);

  if (!document) return <section className="catalog-page receipts-page">
    <button onClick={back}>Volver al listado</button>
    {error ? <p className="notice error" role="alert">{error}</p> : <p role="status">Cargando comprobante…</p>}
  </section>;
  return <MovementDetail document={document} canReverse={canReverse} notice={notice} open={open} back={back}
    reversed={result => { setDocument(result); setNotice("Movimiento anulado. Se registró el movimiento inverso."); }} />;
}

function MovementDetail({ document, canReverse, notice, open, back, reversed }: {
  document: MovementDocument; canReverse: boolean; notice: string;
  open: (id: string) => void; back: () => void; reversed: (document: MovementDocument) => void;
}) {
  const [showReverse, setShowReverse] = useState(false);
  const reverseForm = useRef<HTMLFormElement>(null);
  const [busy, setBusy] = useState(false);
  const [reverseError, setReverseError] = useState("");
  const movement = document.movement;
  const inverse = movement.kind === "VOID";
  const adjust = movement.kind === "ADJUST";
  const noun = adjust ? "ajuste" : "salida";

  useEffect(() => {
    if (!showReverse) return;
    reverseForm.current?.focus({ preventScroll: true });
    reverseForm.current?.scrollIntoView({ block: "center" });
  }, [showReverse]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const reason = String(new FormData(event.currentTarget).get("reason") ?? "").trim();
    if (!reason) { setReverseError("Escribe el motivo de la anulación."); return; }
    setBusy(true);
    setReverseError("");
    try { reversed(await movementApi.reverse(movement.id, reason)); setShowReverse(false); }
    catch (failure) { setReverseError(failureMessage(failure)); }
    finally { setBusy(false); }
  }

  return <section className="catalog-page receipts-page">
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Salidas y ajustes</p>
      <h1>{inverse ? "Movimiento inverso " : "Comprobante "}{movement.folio}</h1>
      <p className="subtitle">Registro interno de inventario.</p></div>
      <button onClick={back}>Volver al listado</button>
    </div>
    {notice && <p className="notice" role="status">{notice}</p>}
    <section className="catalog-panel receipt-panel">
      <div className="receipt-detail-title"><h2>Detalle del movimiento</h2><Status entry={movement} /></div>
      <dl className="receipt-metadata">
        <div><dt>Tipo</dt><dd>{inverse ? "Anulación" : typeLabels[movement.kind as MovementType]}</dd></div>
        <div><dt>Ubicación</dt><dd>{movement.location}</dd></div>
        <div className="receipt-date"><dt>Fecha y hora</dt><dd><time dateTime={movement.date}>{receiptDate(movement.date)}</time></dd></div>
        <div><dt>Responsable</dt><dd>{movement.actor}</dd></div>
        <div><dt>Motivo</dt><dd>{movement.reason}</dd></div>
        <div><dt>Referencia</dt><dd>{movement.reference || "—"}</dd></div>
      </dl>
      {movement.voidId && <p className="receipt-note"><strong>Anulación:</strong> {movement.voidReason}{" "}
        <button className="catalog-link" onClick={() => void open(movement.voidId!)}>Ver {movement.voidFolio}</button></p>}
      {movement.originalId && <button className="catalog-link" onClick={() => void open(movement.originalId!)}>Ver movimiento {movement.originalFolio}</button>}
    </section>
    <section className="catalog-panel receipt-panel">
      <h2>{adjust ? "Resultado del conteo" : inverse ? "Existencias tras la anulación" : "Existencias tras la salida"}</h2>
      <p className="subtitle">Saldo registrado al confirmar; no cambia con movimientos posteriores.</p>
      <table className="receipt-table"><thead><tr><th>Artículo</th>
        {adjust ? <><th>Saldo antes</th><th>Contado</th><th>Diferencia</th></> : <><th>{inverse ? "Cambio" : "Cantidad retirada"}</th><th>Existencia después</th></>}</tr></thead>
        <tbody>{document.lines.map(line => {
          const change = inverse ? <Difference value={line.quantity} unit={line.unit} /> : "−" + amount(Math.abs(line.quantity), line.unit);
          return <tr key={line.itemId}>
            <td><strong>{line.name}</strong><small>{line.sku}</small></td>
            {adjust ? <>
              <td><span className="receipt-mobile-label">Saldo antes</span>{amount(round(line.balanceAfter - line.quantity), line.unit)}</td>
              <td><span className="receipt-mobile-label">Contado</span><strong>{amount(line.balanceAfter, line.unit)}</strong></td>
              <td><span className="receipt-mobile-label">Diferencia</span><Difference value={line.quantity} unit={line.unit} /></td>
            </> : <>
              <td><span className="receipt-mobile-label">{inverse ? "Cambio" : "Retirado"}</span>{change}</td>
              <td><span className="receipt-mobile-label">Existencia después</span><strong>{amount(line.balanceAfter, line.unit)}</strong></td>
            </>}
          </tr>;
        })}</tbody>
      </table>
    </section>
    {!inverse && !movement.voidId && canReverse && (showReverse ?
      <form ref={reverseForm} tabIndex={-1} aria-labelledby="movement-void-title" className="catalog-panel catalog-form" onSubmit={submit} noValidate>
        <h2 id="movement-void-title">Anular {noun}</h2><p className="receipt-note">Se anula completo y una sola vez. Se registrará un movimiento inverso vinculado y el original se conserva.</p>
        <label>Motivo de anulación<input name="reason" required maxLength={150} disabled={busy} aria-invalid={!!reverseError} /></label>
        {reverseError && <p className="notice error" role="alert">{reverseError}</p>}
        <div className="catalog-actions"><button type="button" disabled={busy} onClick={() => { setShowReverse(false); setReverseError(""); }}>Volver</button>
          <button className="receipt-danger" disabled={busy}>{busy ? "Anulando…" : "Confirmar anulación"}</button></div>
      </form> : <div className="catalog-actions"><button onClick={() => setShowReverse(true)}>Anular {noun}</button></div>)}
  </section>;
}
