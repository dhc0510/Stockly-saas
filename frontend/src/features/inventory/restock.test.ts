import { describe, expect, it } from "vitest";
import { suggestOrder, targetError } from "./restock";

const fmt = (value:number) => `${value} m`;

describe("STK-016 · A pedir", () => {
  it("usa el nivel deseado cuando existe", () => {
    expect(suggestOrder(18.5, 25, 10, 100)).toEqual({ toOrder: 81.5, basis: "TARGET" });
  });
  it("sin nivel deseado sugiere hasta salir de alerta (mínimo + margen)", () => {
    expect(suggestOrder(18.5, 25, 10, null)).toEqual({ toOrder: 16.5, basis: "RECOVERY" });
  });
  it("sin mínimo ni nivel deseado no sugiere", () => {
    expect(suggestOrder(0, null, 1, null)).toEqual({ toOrder: null, basis: "NONE" });
  });
  it("nunca sugiere cantidades negativas y conserva 2 decimales exactos", () => {
    expect(suggestOrder(12, 4, 1, 10).toOrder).toBe(0);
    expect(suggestOrder(0.1, null, 1, 0.3).toOrder).toBe(0.2);
    expect(suggestOrder(11.125, 5, 1, 20).toOrder).toBe(8.875);
  });
  it("valida el nivel deseado como el servidor", () => {
    expect(targetError("", 25, fmt)).toBe("");
    expect(targetError("20", 25, fmt)).toBe("El nivel deseado debe ser mayor que el mínimo (25 m).");
    expect(targetError("25", 25, fmt)).toMatch(/mayor que el mínimo/);
    expect(targetError("1.234", null, fmt)).toMatch(/2 decimales/);
    expect(targetError("0", null, fmt)).toMatch(/mayor que cero/);
    expect(targetError("40.5", 25, fmt)).toBe("");
  });
});
