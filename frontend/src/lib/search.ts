/** Prepara un texto para buscar sin distinguir tildes ni mayúsculas: «Café» y «cafe» coinciden. */
export function normalizeSearch(value: string | null | undefined) {
  return (value ?? "").trim().toLocaleLowerCase("es-CR").normalize("NFD").replace(/\p{M}/gu, "");
}
