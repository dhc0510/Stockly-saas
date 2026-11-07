import { describe, expect, it } from "vitest";
import { normalizeSearch } from "./search";

describe("normalizeSearch", () => {
  it("ignora tildes, mayúsculas y espacios externos", () => {
    expect(normalizeSearch("  CAFÉ Molido ")).toBe("cafe molido");
    expect(normalizeSearch("GUÍA-083")).toBe("guia-083");
    expect(normalizeSearch("Pingüino")).toBe("pinguino");
    expect(normalizeSearch("Piña")).toBe("pina");
  });
  it("trata vacíos como texto vacío", () => {
    expect(normalizeSearch(null)).toBe("");
    expect(normalizeSearch(undefined)).toBe("");
  });
  it("permite comparar lo escrito con el dato", () => {
    expect(normalizeSearch("Té verde 100 g").includes(normalizeSearch("te ver"))).toBe(true);
  });
});
