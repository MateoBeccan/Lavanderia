import { describe, expect, it } from "vitest";
import {
  aperturaSchema,
  cierreSchema,
  filtrosSchema,
  movimientoManualSchema,
  validarEgreso,
} from "./caja";

describe("contrato de Caja", () => {
  it.each([
    ["0", 0],
    [" 12,35 ", 12.35],
    ["99999999.99", 99999999.99],
  ])("acepta %s como efectivo contado", (efectivoContado, esperado) => {
    expect(cierreSchema.parse({ efectivoContado }).efectivoContado).toBe(
      esperado,
    );
  });
  it.each(["", "-0,01", "1,001", "1e2", "100000000", "NaN", "1.000,50"])(
    "rechaza el importe %s",
    (efectivoContado) => {
      expect(cierreSchema.safeParse({ efectivoContado }).success).toBe(false);
    },
  );
  it("valida los extremos de fecha inclusivos", () => {
    const filtros = {
      tipo: "",
      metodoPagoId: "",
      desde: "2026-11-18",
      hasta: "2026-11-18",
    };
    expect(filtrosSchema.safeParse(filtros).success).toBe(true);
    expect(
      filtrosSchema.safeParse({ ...filtros, hasta: "2026-11-17" }).success,
    ).toBe(false);
    expect(
      filtrosSchema.safeParse({ ...filtros, hasta: "2026-02-30" }).success,
    ).toBe(false);
  });
});

describe("escrituras de Caja", () => {
  const movimiento = {
    monto: "0,01",
    metodoPagoId: "2",
    descripcion: " Fondo ",
  };
  it("admite abrir en cero y convierte los importes decimales", () => {
    expect(aperturaSchema.parse({ montoInicial: "0" })).toEqual({
      montoInicial: 0,
    });
    expect(aperturaSchema.safeParse({ montoInicial: "-1" }).success).toBe(
      false,
    );
    expect(movimientoManualSchema.parse(movimiento)).toEqual({
      monto: 0.01,
      metodoPagoId: 2,
      descripcion: "Fondo",
    });
  });
  it.each(["0", "-1", "1,001", "1e2", "100000000"])(
    "rechaza %s como importe de movimiento",
    (monto) => {
      expect(
        movimientoManualSchema.safeParse({ ...movimiento, monto }).success,
      ).toBe(false);
    },
  );
  it("exige un método y un concepto de hasta 250 caracteres", () => {
    expect(
      movimientoManualSchema.safeParse({ ...movimiento, metodoPagoId: "" })
        .success,
    ).toBe(false);
    expect(
      movimientoManualSchema.safeParse({ ...movimiento, descripcion: "  " })
        .success,
    ).toBe(false);
    expect(
      movimientoManualSchema.safeParse({
        ...movimiento,
        descripcion: "x".repeat(251),
      }).success,
    ).toBe(false);
  });
  it("limita el egreso en efectivo al disponible y admite el importe exacto", () => {
    expect(validarEgreso(5.01, "EFECTIVO", 5)).toMatch(/supera el efectivo/);
    expect(validarEgreso(5, "EFECTIVO", 5)).toBeUndefined();
    expect(validarEgreso(5.01, "TRANSFERENCIA", 5)).toBeUndefined();
  });
});
