import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/shared/api/http";
import { requiereConsulta } from "../components/presentacion";
import { comprobanteSchema } from "./comprobante";

afterEach(() => vi.useRealTimers());

describe("alta de comprobante", () => {
  it("usa el día comercial de Buenos Aires y admite fechas históricas", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-20T01:30:00Z"));
    for (const fechaEmision of ["2026-11-19", "2024-02-29", "0001-01-01"]) {
      expect(
        comprobanteSchema.parse({ fechaEmision, observaciones: " " }),
      ).toEqual({
        fechaEmision,
        observaciones: null,
      });
    }
    for (const fechaEmision of [
      "",
      "2026-11-20",
      "0000-01-01",
      "2026-02-29",
      "19/11/2026",
    ]) {
      expect(
        comprobanteSchema.safeParse({ fechaEmision, observaciones: "" })
          .success,
      ).toBe(false);
    }
  });

  it("recorta observaciones y valida 200 puntos de código", () => {
    const fechaEmision = "2024-02-29";
    const texto = "🧺".repeat(200);
    expect(
      comprobanteSchema.parse({
        fechaEmision,
        observaciones: "  " + texto + "  ",
      }).observaciones,
    ).toBe(texto);
    expect(
      comprobanteSchema.safeParse({ fechaEmision, observaciones: texto + "a" })
        .success,
    ).toBe(false);
  });

  it.each([0, 201, 408, 409, 500, 503])(
    "requiere una lectura de verificación tras %i",
    (status) => {
      expect(requiereConsulta(new ApiError(status, "Error"))).toBe(true);
    },
  );

  it.each([400, 401, 403, 404])(
    "maneja %i como un rechazo confirmado",
    (status) => {
      expect(requiereConsulta(new ApiError(status, "Rechazado"))).toBe(false);
    },
  );
});
