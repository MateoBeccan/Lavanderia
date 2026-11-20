import { describe, expect, it } from "vitest";
import { ApiError } from "@/shared/api/http";
import { errorOperacionCaja } from "./errorOperacionCaja";

describe("resultado de las escrituras de Caja", () => {
  it.each([
    ["2", 3_000],
    [new Date(5_000).toUTCString(), 5_000],
    [undefined, 2_000],
  ])("respeta Retry-After %s", (cabecera, retryAt) => {
    const error = new ApiError(409, "", [], "IDEMPOTENCIA_EN_CURSO", cabecera);
    expect(errorOperacionCaja(error, false, 1_000)).toMatchObject({
      estado: "PENDIENTE",
      retryAt,
    });
  });
  it("distingue la clave reutilizada del conflicto comercial", () => {
    const reutilizada = new ApiError(
      409,
      "",
      [],
      "IDEMPOTENCIA_CLAVE_REUTILIZADA",
    );
    expect(errorOperacionCaja(reutilizada, false).estado).toBe("VERIFICAR");
    expect(
      errorOperacionCaja(
        new ApiError(409, "Ya existe una caja abierta."),
        false,
      ),
    ).toEqual({ estado: "ERROR", mensaje: "Ya existe una caja abierta." });
  });
  it.each([0, 201, 408, 503])(
    "conserva el resultado incierto ante HTTP %s",
    (status) => {
      expect(errorOperacionCaja(new ApiError(status, ""), false).estado).toBe(
        "PENDIENTE",
      );
    },
  );
  it("conserva un intento previo durante un fallo de red o de autenticación", () => {
    expect(
      errorOperacionCaja(new DOMException("", "TimeoutError"), false).estado,
    ).toBe("PENDIENTE");
    const sesion = new ApiError(401, "");
    expect(errorOperacionCaja(sesion, true).estado).toBe("PENDIENTE");
    expect(errorOperacionCaja(sesion, false).estado).toBe("ERROR");
  });
});
