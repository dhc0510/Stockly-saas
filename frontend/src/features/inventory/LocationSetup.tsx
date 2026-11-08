import { useEffect, useState } from "react";
import { request } from "../../lib/http";
import type { Branch } from "./api";

type Setup = { branches: Branch[]; needsPrincipal: boolean; pending: boolean };
const url = "/api/inventory/location-setup";
const message = (error: unknown) => error instanceof Error ? error.message : "No se pudo configurar el inventario.";

export function LocationSetup({ onConfigured }: { onConfigured: () => void }) {
  const [setup, setSetup] = useState<Setup>();
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    request<Setup>(url, { signal: controller.signal }).then(result => {
      setSetup(result);
      if (result.branches.length === 1) setSelected(result.branches[0].id);
    }).catch(failure => { if (!controller.signal.aborted) setError(message(failure)); });
    return () => controller.abort();
  }, []);

  async function configure() {
    setSaving(true);
    setError("");
    try {
      setSetup(await request<Setup>(url, { method: "POST", body: JSON.stringify({ branchId: selected || null }) }));
      onConfigured();
    } catch (failure) {
      setError(message(failure));
    } finally {
      setSaving(false);
    }
  }

  if (!setup?.pending) return error ? <p role="alert">{error}</p> : null;

  return <section className="catalog-panel receipt-panel" aria-labelledby="location-setup-title">
    <h2 id="location-setup-title">Configurar inventario por sucursal</h2>
    {setup.needsPrincipal ? <>
      <p>La sucursal que confirmes conservará las existencias y el historial de Principal.</p>
      <label>Sucursal de Principal<select value={selected} onChange={event => setSelected(event.target.value)}>
        <option value="">Seleccionar sucursal</option>
        {setup.branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
      </select></label>
    </> : <p>Habilita el inventario para las sucursales que todavía no tienen ubicación.</p>}
    {error && <p className="notice error" role="alert">{error}</p>}
    <button className="button primary" type="button" disabled={saving || (setup.needsPrincipal && !selected)} onClick={() => void configure()}>
      {saving ? "Guardando…" : "Confirmar configuración"}
    </button>
  </section>;
}
