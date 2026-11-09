import { describe, expect, it } from "vitest";
import { periodError, presetRange, todayCR, variation } from "./period";

describe("STK-017 períodos", () => {
  it("usa el día de Costa Rica (UTC−6)", () => {
    expect(todayCR(new Date("2026-11-09T05:59:00Z"))).toBe("2026-11-08");
    expect(todayCR(new Date("2026-11-09T06:00:00Z"))).toBe("2026-11-09");
  });
  it("calcula los períodos predefinidos", () => {
    expect(presetRange("today", "2026-11-08")).toEqual({ from: "2026-11-08", to: "2026-11-08" });
    expect(presetRange("yesterday", "2026-11-01")).toEqual({ from: "2026-10-31", to: "2026-10-31" });
    expect(presetRange("last7", "2026-11-08")).toEqual({ from: "2026-11-02", to: "2026-11-08" });
    expect(presetRange("month", "2026-11-08")).toEqual({ from: "2026-11-01", to: "2026-11-08" });
    expect(presetRange("previousMonth", "2026-03-15")).toEqual({ from: "2026-02-01", to: "2026-02-28" });
    expect(presetRange("previousMonth", "2026-01-10")).toEqual({ from: "2025-12-01", to: "2025-12-31" });
  });
  it("valida el período como el servidor", () => {
    expect(periodError("2026-11-08", "2026-11-01", "2026-11-08")).toMatch(/«Desde»/);
    expect(periodError("2026-11-01", "2026-11-09", "2026-11-08")).toMatch(/futuras/);
    expect(periodError("2025-11-07", "2026-11-08", "2026-11-08")).toMatch(/366/);
    expect(periodError("2025-11-08", "2026-11-08", "2026-11-08")).toBeNull();
    expect(periodError("", "2026-11-08", "2026-11-08")).toMatch(/dos fechas/);
  });
  it("calcula la variación redondeada y sin datos si el anterior es 0", () => {
    expect(variation(110, 100)).toBe(10);
    expect(variation(51900, 61200)).toBe(-15);
    expect(variation(5, 0)).toBeNull();
  });
});
