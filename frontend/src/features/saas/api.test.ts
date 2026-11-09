import { describe, expect, it } from "vitest";
import { addPeriods, taxBreakdown } from "./api";

describe("STK-018 vista previa del pago", () => {
  it("desglosa base e IVA del 13 % incluido como la factura", () => {
    expect(taxBreakdown(15000)).toEqual({ base: 13274.34, tax: 1725.66 });
    expect(taxBreakdown(150000)).toEqual({ base: 132743.36, tax: 17256.64 });
  });
  it("calcula el fin del período como el servidor (fin de mes ajustado)", () => {
    expect(addPeriods("2026-11-05", "MONTHLY", 1)).toBe("2026-12-05");
    expect(addPeriods("2026-01-31", "MONTHLY", 1)).toBe("2026-02-28");
    expect(addPeriods("2026-11-05", "MONTHLY", 3)).toBe("2027-02-05");
    expect(addPeriods("2026-11-05", "ANNUAL", 1)).toBe("2027-11-05");
  });
});
