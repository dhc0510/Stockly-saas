import { useEffect, useState } from "react";
import { request } from "../../lib/http";

type BranchOption = { id: string; name: string; location: string | null };
type Choices = { branches: BranchOption[]; legacy: boolean };

export function useMovementBranch(base: string, selectedId?: string) {
  const [choices, setChoices] = useState<Choices>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    request<Choices>(base + "/branches", { signal: controller.signal }).then(setChoices).catch(failure => {
      if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "No se pudieron cargar las sucursales.");
    });
    return () => controller.abort();
  }, [base]);
  const branches = choices?.branches ?? [];
  const selected = branches.find(branch => branch.id === (selectedId ?? (branches.length === 1 ? branches[0].id : undefined)));
  const problem = error || (!choices ? "Cargando sucursales…" : choices.legacy ? "" :
    !branches.length ? "No tienes acceso a una sucursal activa." :
    !selected ? "Selecciona una sucursal." :
    !selected.location ? "El administrador debe configurar el inventario de esta sucursal en Existencias." : "");
  return { branches, selected, legacy: choices?.legacy, problem };
}

export function MovementBranch({ state, locked, onChange }: {
  state: ReturnType<typeof useMovementBranch>;
  locked: boolean;
  onChange: (id: string | undefined) => void;
}) {
  if (state.legacy) return null;
  return <div>
    {state.branches.length > 0 && <label>Sucursal
      <select value={state.selected?.id ?? ""} disabled={locked || state.branches.length === 1}
        onChange={event => onChange(event.target.value || undefined)}>
        <option value="">Seleccionar sucursal</option>
        {state.branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
      </select>
    </label>}
    {state.problem && <p role="status">{state.problem}</p>}
    {locked && state.branches.length > 1 && <small>Quita las líneas para cambiar de sucursal.</small>}
  </div>;
}
