import { useEffect, useRef, useState, type FormEvent } from "react";
import { Plus, Search, X } from "lucide-react";
import { ApiError } from "../../lib/http";
import { amount, failureMessage } from "../receipts/receiptApi";
import { movementApi, round, type Article, type MovementDocument, type MovementType } from "./movementApi";

const MAX_LINES = 200;
const MAX_QUANTITY = 1_000_000;
const reasons: Record<MovementType, Record<string, string>> = {
  OUT: { DAMAGE: "Daño o rotura", EXPIRED: "Vencimiento", THEFT: "Robo o faltante", INTERNAL: "Consumo interno", OTHER: "Otro" },
  ADJUST: { COUNT: "Conteo físico", CORRECTION: "Corrección de error", OTHER: "Otro" },
};
type DraftLine = { article: Article; quantity: string };
type Draft = {
  reason: string; detail: string; reference: string; lines: DraftLine[];
  search: string; article?: Article; quantity: string; requestKey?: string;
};
const empty = (): Draft => ({ reason: "", detail: "", reference: "", lines: [], search: "", quantity: "" });

function load(key: string): Draft {
  try { return { ...empty(), ...JSON.parse(localStorage.getItem(key) || "{}") }; }
  catch { return empty(); }
}
export function movementDraftStatus(key: string) {
  const draft = load(key);
  if (draft.requestKey) return "pending";
  if (draft.lines.length || draft.reason || draft.detail.trim() || draft.reference.trim() || draft.article || draft.search.trim() || draft.quantity.trim())
    return "editing";
}

function parse(value: string) {
  const text = value.trim();
  return /^\d+(?:[.,]\d+)?$/.test(text) ? Number(text.replace(",", ".")) : NaN;
}
function quantityError(value: string, unit: string, allowZero: boolean) {
  const quantity = parse(value);
  if (!Number.isFinite(quantity) || quantity < 0 || (!allowZero && quantity === 0))
    return allowZero ? "Ingresa la cantidad contada; puede ser cero." : "Ingresa una cantidad mayor que cero.";
  if (unit === "unidad" && !Number.isInteger(quantity)) return "Para unidad solo se aceptan números enteros.";
  if ((value.trim().split(/[.,]/)[1]?.length ?? 0) > 3) return "Ingresa hasta tres decimales.";
  if (quantity > MAX_QUANTITY) return "El máximo por línea es 1.000.000.";
  return "";
}

export function Difference({ value, unit }: { value: number; unit: string }) {
  if (value === 0) return <span className="movement-same">Sin diferencia</span>;
  return <span className={value > 0 ? "movement-up" : "movement-down"}>{value > 0 ? "+" : "−"}{amount(Math.abs(value), unit)}</span>;
}

export function MovementEditor({ type, storageKey, close, saved }: {
  type: MovementType; storageKey: string; close: () => void; saved: (document: MovementDocument) => void;
}) {
  const adjust = type === "ADJUST";
  const noun = adjust ? "ajuste" : "salida";
  const [draft, setDraft] = useState(() => load(storageKey));
  const currentDraft = useRef(draft);
  const [storageError, setStorageError] = useState(false);
  const [suggestions, setSuggestions] = useState<Article[]>([]);
  const [searching, setSearching] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [review, setReview] = useState(!!draft.requestKey);
  const [discard, setDiscard] = useState(false);
  const discardPanel = useRef<HTMLElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [changes, setChanges] = useState<{ name: string; unit: string; before: number; after: number; difference: number }[]>([]);
  const [reasonInvalid, setReasonInvalid] = useState(false);
  const reasonInput = useRef<HTMLSelectElement>(null);
  const [quantityIssue, setQuantityIssue] = useState<{ id: string; message: string }>();
  const [notice, setNotice] = useState("");
  const [flash, setFlash] = useState("");
  const searchInput = useRef<HTMLInputElement>(null);
  const quantityInput = useRef<HTMLInputElement>(null);
  const results = useRef<HTMLDivElement>(null);
  const pending = !!draft.article || !!draft.quantity.trim();
  const term = draft.search.trim();

  useEffect(() => {
    if (!showSuggestions || !term || draft.article) return;
    const controller = new AbortController();
    setSearching(true);
    setSuggestions([]);
    const timer = window.setTimeout(() => {
      movementApi.items(term, false, controller.signal).then(items => {
        if (!controller.signal.aborted) setSuggestions(items);
      }).catch(failure => {
        if (!controller.signal.aborted) { setNotice(""); setError(failureMessage(failure)); }
      }).finally(() => { if (!controller.signal.aborted) setSearching(false); });
    }, 150);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [term, showSuggestions, draft.article]);

  useEffect(() => {
    function align() {
      const input = searchInput.current;
      if (!input || document.activeElement !== input || !matchMedia("(max-width: 760px)").matches) return;
      input.closest(".receipt-item-search")?.scrollIntoView({ block: "start", behavior: "instant" });
      requestAnimationFrame(() => {
        const viewport = window.visualViewport;
        const space = (viewport?.height ?? innerHeight) + (viewport?.offsetTop ?? 0) - input.getBoundingClientRect().bottom - 12;
        input.closest<HTMLElement>(".receipt-item-search")?.style.setProperty("--receipt-results-height", Math.max(72, Math.min(240, space)) + "px");
      });
    }
    const input = searchInput.current;
    input?.addEventListener("focus", align);
    window.visualViewport?.addEventListener("resize", align);
    return () => { input?.removeEventListener("focus", align); window.visualViewport?.removeEventListener("resize", align); };
  }, [review]);

  useEffect(() => {
    if (!discard) return;
    discardPanel.current?.focus({ preventScroll: true });
    discardPanel.current?.scrollIntoView({ block: "center" });
  }, [discard]);

  function store(next: Draft) {
    currentDraft.current = next;
    setDraft(next);
    try { localStorage.setItem(storageKey, JSON.stringify(next)); setStorageError(false); return true; }
    catch { setStorageError(true); return false; }
  }
  function clear() {
    try { localStorage.removeItem(storageKey); }
    catch { setStorageError(true); }
  }
  function change(patch: Partial<Draft>) {
    setError(""); setNotice(""); setFlash(""); setQuantityIssue(undefined);
    store({ ...currentDraft.current, ...patch });
  }
  function fail(message: string) { setNotice(""); setError(message); }
  function select(article: Article) {
    change({ article, search: article.name + " · " + article.sku, quantity: "" });
    setShowSuggestions(false);
    quantityInput.current?.focus();
  }
  function markQuantity(id: string, message: string) {
    setError(""); setNotice(""); setQuantityIssue({ id, message });
    document.getElementById("movement-quantity-" + id)?.focus();
  }

  function add(article: Article, raw: string) {
    const draft = currentDraft.current;
    const validation = quantityError(raw, article.unit, adjust);
    if (validation) { markQuantity("pending", validation); return; }
    const line = draft.lines.find(value => value.article.id === article.id);
    const added = parse(raw);
    if (line) {
      const lineError = quantityError(line.quantity, article.unit, adjust);
      if (lineError) { markQuantity(article.id, lineError); return; }
    }
    const total = line ? round(parse(line.quantity) + added) : added;
    if (total > MAX_QUANTITY) { markQuantity(line ? article.id : "pending", "El máximo por línea es 1.000.000."); return; }
    if (!line && draft.lines.length >= MAX_LINES) { fail("Admite hasta 200 líneas."); return; }
    const quantity = String(total).replace(".", ",");
    const lines = line ? draft.lines.map(value => value === line ? { ...line, quantity } : value)
      : [...draft.lines, { article, quantity }];
    store({ ...draft, lines, article: undefined, search: "", quantity: "" });
    setShowSuggestions(false);
    setError("");
    setChanges([]);
    setQuantityIssue(undefined);
    setFlash(article.id);
    setNotice(line ? (added === 1 ? "Se sumó " : "Se sumaron ") + amount(added, article.unit) + ". Total: " + amount(total, article.unit) + "." : "Artículo agregado.");
    searchInput.current?.focus();
  }

  async function scan() {
    if (!term || scanning) return;
    if (draft.article) { quantityInput.current?.focus(); return; }
    setScanning(true);
    setShowSuggestions(false);
    try {
      const [match] = await movementApi.items(term, true);
      if (match) {
        if (match.unit === "unidad") add(match, "1");
        else select(match);
      } else {
        const matches = await movementApi.items(term, false);
        const exact = matches.find(article => article.sku.toLocaleLowerCase() === term.toLocaleLowerCase());
        if (exact) select(exact);
        else if (matches.length) { setSuggestions(matches); setShowSuggestions(true); }
        else fail("No encontramos un artículo activo con ese código o búsqueda.");
      }
    } catch (failure) { fail(failureMessage(failure)); }
    finally { setScanning(false); }
  }

  function reviewFailure(message: string) {
    fail(message);
    requestAnimationFrame(() => document.querySelector(".receipt-items [role=alert]")?.scrollIntoView({ block: "nearest" }));
  }

  function startReview() {
    if (pending) { reviewFailure("Hay un artículo sin agregar. Agrégalo o limpia el artículo y la cantidad antes de revisar."); return; }
    if (!draft.reason) {
      setError(""); setNotice(""); setQuantityIssue(undefined); setReasonInvalid(true);
      reasonInput.current?.focus({ preventScroll: true });
      reasonInput.current?.scrollIntoView({ block: "center" });
      return;
    }
    if (draft.reason === "OTHER" && !draft.detail.trim()) { reviewFailure("Escribe el detalle del motivo Otro."); return; }
    for (const line of draft.lines) {
      const validation = quantityError(line.quantity, line.article.unit, adjust);
      if (validation) { markQuantity(line.article.id, validation); return; }
      if (!adjust && parse(line.quantity) > line.article.balance) {
        markQuantity(line.article.id, "Dejaría saldo negativo. Existencia actual: " + amount(line.article.balance, line.article.unit) + "."); return;
      }
    }
    setError(""); setNotice(""); setReview(true);
  }

  /** El servidor no registró nada: actualiza los saldos vistos y vuelve a edición conservando lo escrito. */
  function refreshBalances(fields: Record<string, string>, base: Draft) {
    const lines = base.lines.map(line => line.article.id in fields
      ? { ...line, article: { ...line.article, balance: Number(fields[line.article.id]) } } : line);
    if (adjust) setChanges(base.lines.filter(line => line.article.id in fields).map(line => {
      const after = Number(fields[line.article.id]);
      return { name: line.article.name, unit: line.article.unit, before: line.article.balance, after, difference: round(parse(line.quantity) - after) };
    }));
    store({ ...base, lines, requestKey: undefined });
    setReview(false);
  }

  async function confirm() {
    if (busy) return;
    const key = draft.requestKey ?? crypto.randomUUID();
    const pendingDraft = { ...draft, requestKey: key };
    if (!store(pendingDraft)) { fail("Permite el almacenamiento local para confirmar con recuperación ante fallos."); return; }
    setBusy(true); setError(""); setChanges([]);
    const common = { requestKey: key, reason: draft.reason, detail: draft.detail, reference: draft.reference };
    try {
      const document = adjust
        ? await movementApi.adjustment({ ...common, lines: draft.lines.map(line => ({ itemId: line.article.id, countedQuantity: parse(line.quantity), expectedBalance: line.article.balance })) })
        : await movementApi.outflow({ ...common, lines: draft.lines.map(line => ({ itemId: line.article.id, quantity: parse(line.quantity) })) });
      clear();
      saved(document);
    } catch (failure) {
      if (failure instanceof ApiError && (failure.code === "BALANCE_CHANGED" || failure.code === "INSUFFICIENT_BALANCE")) {
        refreshBalances(failure.fieldErrors, pendingDraft);
      } else if (failure instanceof ApiError && (failure.status === 400 || (failure.status === 409 && !draft.requestKey))) {
        store({ ...pendingDraft, requestKey: undefined });
        setReview(false);
      }
      fail(failureMessage(failure));
    } finally { setBusy(false); }
  }

  return <section className={"catalog-page receipts-page" + (review ? "" : " receipt-editor")}>
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Salidas y ajustes</p>
      <h1>{review ? "Revisar " + noun : adjust ? "Nuevo ajuste" : "Nueva salida"}</h1>
      <p className="subtitle">{review ? "Verifica los artículos antes de registrar el movimiento."
        : adjust ? "Escribe lo que contaste. Stockly calcula la diferencia y deja ese saldo al confirmar." : "Las existencias bajan al confirmar la salida."}</p></div>
      <button onClick={close} disabled={busy || scanning}>Volver al listado</button>
    </div>
    {storageError && <p className="notice error" role="alert">El navegador no pudo guardar el borrador local.</p>}
    {review ? <>
      {draft.requestKey && <p className="receipt-note">Hay una confirmación pendiente. Puedes reintentar con los mismos datos.</p>}
      <section className="catalog-panel receipt-panel">
        <h2>Ubicación Principal</h2>
        <p>{reasons[type][draft.reason]}{draft.reason === "OTHER" && " · " + draft.detail}</p>
        {draft.reference && <p>Referencia: {draft.reference}</p>}
        <ul className="receipt-review-lines">{draft.lines.map(line => {
          const value = parse(line.quantity);
          return <li key={line.article.id}>
            <span>{line.article.name}<small>{line.article.sku}</small></span>
            {adjust ? <strong>{amount(line.article.balance, line.article.unit)} → {amount(value, line.article.unit)} (<Difference value={round(value - line.article.balance)} unit={line.article.unit} />)</strong>
              : <strong>−{amount(value, line.article.unit)}</strong>}
          </li>;
        })}</ul>
      </section>
      {error && <p className="notice error" role="alert">{error}</p>}
      <div className="catalog-actions">
        {!draft.requestKey && <button onClick={() => setReview(false)} disabled={busy}>Volver a editar</button>}
        <button className="catalog-primary" onClick={() => void confirm()} disabled={busy}>{busy ? "Confirmando…" : draft.requestKey ? "Reintentar confirmación" : "Confirmar " + noun}</button>
      </div>
    </> : <>
      {changes.length > 0 && <div className="notice error movement-changes" role="alert">
        <strong>Revisa la nueva diferencia</strong>
        <ul>{changes.map(item => <li key={item.name}>{item.name}: saldo {amount(item.before, item.unit)} → {amount(item.after, item.unit)}. Nueva diferencia: <Difference value={item.difference} unit={item.unit} /></li>)}</ul>
      </div>}
      <section className="catalog-panel receipt-panel">
        <h2>{adjust ? "Datos del ajuste" : "Datos de la salida"}</h2>
        <div className="receipt-fields">
          <label>Motivo obligatorio<select ref={reasonInput} value={draft.reason} aria-invalid={reasonInvalid}
            aria-describedby={reasonInvalid ? "movement-reason-error" : undefined}
            onChange={event => { setReasonInvalid(false); change({ reason: event.target.value }); }}>
            <option value="">Selecciona un motivo</option>{Object.entries(reasons[type]).map(([key, value]) => <option key={key} value={key}>{value}</option>)}
          </select>{reasonInvalid && <small className="receipt-quantity-error" id="movement-reason-error" role="alert">Selecciona el motivo.</small>}</label>
          <label>Referencia opcional<input maxLength={100} value={draft.reference} onChange={event => change({ reference: event.target.value })} /></label>
          {draft.reason === "OTHER" && <label className="receipt-reason-detail">Detalle obligatorio<input maxLength={150} value={draft.detail} onChange={event => change({ detail: event.target.value })} /></label>}
        </div>
        <p className="receipt-note"><strong>Ubicación</strong> · Principal</p>
      </section>
      <section className="catalog-panel receipt-panel receipt-items">
        <h2>{adjust ? "Artículos contados" : "Artículos que salen"}</h2>
        <form className="receipt-add" onSubmit={(event: FormEvent) => { event.preventDefault(); if (draft.article) add(draft.article, draft.quantity); }}>
          <div className="receipt-item-search">
            <label>Buscar artículo<span className="catalog-search"><Search size={16} aria-hidden="true" />
              <input ref={searchInput} type="search" autoComplete="off" maxLength={160} value={draft.search}
                placeholder="Nombre, SKU o código de barras" readOnly={scanning}
                onFocus={() => { if (!draft.article) setShowSuggestions(true); }}
                onChange={event => { change({ search: event.target.value, article: undefined }); setShowSuggestions(true); }}
                onKeyDown={event => {
                  if (event.key === "Escape") setShowSuggestions(false);
                  if (event.key === "ArrowDown") { event.preventDefault(); results.current?.querySelector("button")?.focus(); }
                  if (event.key === "Enter") { event.preventDefault(); void scan(); }
                }} />
            </span></label>
            <small className="subtitle">{adjust ? "Cada lectura del lector suma 1 a lo contado. Para kg, litros o metros, escribe la cantidad." : "Escanea un código de barras y pulsa Enter. Cada lectura suma 1."}</small>
            {showSuggestions && term && !draft.article && <div className="receipt-results" ref={results}>
              {searching ? <p role="status">Buscando…</p> : suggestions.length ? suggestions.map(article =>
                <button type="button" key={article.id} onClick={() => select(article)}><strong>{article.name}</strong><small>{article.sku} · Existencia: {amount(article.balance, article.unit)}</small></button>
              ) : <p>No encontramos artículos.</p>}
            </div>}
          </div>
          <div className="receipt-quantity">
            <label>{adjust ? "Contado" : "Cantidad"}<input id="movement-quantity-pending" ref={quantityInput} aria-invalid={quantityIssue?.id === "pending"} aria-describedby={quantityIssue?.id === "pending" ? "movement-error-pending" : undefined} inputMode="decimal" autoComplete="off" value={draft.quantity}
              onChange={event => change({ quantity: event.target.value })} /></label>
            {draft.article && <><span>{draft.article.unit}</span><small>Existencia actual: {amount(draft.article.balance, draft.article.unit)}</small></>}
            {quantityIssue?.id === "pending" && <small className="receipt-quantity-error" id="movement-error-pending" role="alert">{quantityIssue.message}</small>}
          </div>
          <button type="submit" disabled={!draft.article || scanning}><Plus size={16} /> Agregar línea</button>
          {pending && <button type="button" onClick={() => change({ article: undefined, search: "", quantity: "" })}>Limpiar artículo</button>}
        </form>
        {error && <p className="notice error" role="alert">{error}</p>}
        {notice && !error && <p className="notice receipt-success" role="status">{notice}</p>}
        {draft.lines.length ? <table className="receipt-table movement-lines"><thead><tr><th>Artículo</th>
          {adjust ? <><th>Saldo en Stockly</th><th>Contado</th><th>Diferencia</th></> : <><th>Cantidad</th><th>Existencia actual</th><th>Quedará</th></>}<th>Acción</th></tr></thead>
          <tbody>{draft.lines.map(line => {
            const { article } = line, value = parse(line.quantity), valid = Number.isFinite(value), after = round(article.balance - value);
            const invalid = quantityIssue?.id === article.id;
            const input = <><input id={"movement-quantity-" + article.id} aria-label={(adjust ? "Cantidad contada de " : "Cantidad de ") + article.sku}
              aria-invalid={invalid} aria-describedby={invalid ? "movement-error-" + article.id : undefined} inputMode="decimal" value={line.quantity}
              onChange={event => change({ lines: draft.lines.map(item => item === line ? { ...line, quantity: event.target.value } : item) })} /><small>{article.unit}</small>
              {invalid && <small className="receipt-quantity-error" id={"movement-error-" + article.id} role="alert">{quantityIssue.message}</small>}</>;
            return <tr key={article.id} className={invalid ? "receipt-invalid-line" : flash === article.id ? "receipt-flash" : undefined}>
              <td><strong>{article.name}</strong><small>{article.sku}</small></td>
              {adjust ? <>
                <td><span className="receipt-mobile-label">Saldo en Stockly</span>{amount(article.balance, article.unit)}</td>
                <td><span className="receipt-mobile-label">Contado</span>{input}</td>
                <td><span className="receipt-mobile-label">Diferencia</span>{valid ? <Difference value={round(value - article.balance)} unit={article.unit} /> : "—"}</td>
              </> : <>
                <td><span className="receipt-mobile-label">Cantidad</span>{input}</td>
                <td><span className="receipt-mobile-label">Existencia actual</span>{amount(article.balance, article.unit)}</td>
                <td><span className="receipt-mobile-label">Quedará</span>{!valid ? "—" : after < 0 ? <span className="movement-down">Dejaría saldo negativo</span> : amount(after, article.unit)}</td>
              </>}
              <td><button type="button" aria-label={"Quitar " + article.sku} onClick={() => change({ lines: draft.lines.filter(item => item !== line) })}><X size={17} /></button></td>
            </tr>;
          })}</tbody></table> : <p className="subtitle">Todavía no agregaste artículos.</p>}
      </section>
      {discard ? <section ref={discardPanel} tabIndex={-1} aria-labelledby="movement-discard-title" className="catalog-panel receipt-panel"><h2 id="movement-discard-title">Descartar {noun}</h2><p>Se borrará lo escrito en este navegador.</p>
        <div className="catalog-actions"><button onClick={() => setDiscard(false)}>Volver</button>
          <button className="receipt-danger" onClick={() => { clear(); close(); }}>Descartar {noun}</button></div></section>
        : <div className="catalog-actions receipt-editor-actions"><button disabled={scanning} onClick={() => setDiscard(true)}>Descartar</button>
          <button className="catalog-primary" disabled={scanning || (!draft.lines.length && !pending)} onClick={startReview}>Revisar {noun}</button></div>}
    </>}
  </section>;
}
