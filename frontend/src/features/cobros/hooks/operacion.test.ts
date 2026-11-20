import { describe, expect, it } from "vitest";
import { ApiError } from "@/shared/api/http";
import { resolverError } from "./operacion";
import type { IntentoCobro } from "../types/cobros";

const intento: IntentoCobro = {
  pedidoId: "27",
  clave: "4d51adaf-7660-4085-aebf-74fe4375fe85",
  body: { monto: 12.53, metodoPagoId: 1 },
};

describe("resultado financiero", () => {
  it.each([0, 201, 408, 502])(
    "conserva el intento ante una respuesta ambigua (%s)",
    (status) => {
      expect(
        resolverError(new ApiError(status, "Error"), intento, false),
      ).toMatchObject({
        estado: "RESULTADO_INCIERTO",
        intento,
      });
    },
  );

  it.each([400, 401, 403, 429])(
    "preserva un intento anterior si el reintento recibe %s",
    (status) => {
      const error = new ApiError(status, "Error");
      expect(resolverError(error, intento, false).estado).toBe(
        "ERROR_VALIDADO",
      );
      expect(resolverError(error, intento, true)).toMatchObject({
        estado: "RESULTADO_INCIERTO",
        intento,
      });
    },
  );

  it("distingue una operación en curso, una colisión y un conflicto del dominio", () => {
    const enCurso = new ApiError(409, "", [], "IDEMPOTENCIA_EN_CURSO", "2");
    const reutilizada = new ApiError(
      409,
      "",
      [],
      "IDEMPOTENCIA_CLAVE_REUTILIZADA",
    );
    expect(resolverError(enCurso, intento, false, 1000)).toMatchObject({
      estado: "CONFLICTO",
      retryAt: 3000,
      intento,
    });
    expect(
      resolverError(
        new ApiError(409, "", [], undefined, "2"),
        intento,
        false,
        1000,
      ),
    ).toMatchObject({ estado: "CONFLICTO", retryAt: 3000, intento });
    expect(resolverError(reutilizada, intento, true)).toMatchObject({
      estado: "CONFLICTO",
      retryAt: null,
      intento,
    });
    expect(
      resolverError(new ApiError(409, "Saldo insuficiente"), intento, false),
    ).toMatchObject({
      estado: "ERROR_VALIDADO",
      mensaje: "Saldo insuficiente",
    });
  });
});
