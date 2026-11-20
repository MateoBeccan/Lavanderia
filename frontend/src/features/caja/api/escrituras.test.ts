import { describe, expect, it, vi } from "vitest";
import { registrarCaja } from "./escrituras";
import type { IntentoCaja } from "./escrituras";

const intento = {
  tipo: "INGRESO_MANUAL",
  cajaId: 4,
  clave: "2ec91266-4fb3-4c19-8bdf-07429676b652",
  body: { monto: 10, metodoPagoId: 1, descripcion: "Fondo adicional" },
} satisfies IntentoCaja;

const movimiento = {
  id: 7,
  cajaId: 4,
  monto: 10,
  descripcion: "Fondo adicional",
  metodoPagoNombre: "Efectivo",
  tipoMovimientoCodigo: "INGRESO_MANUAL",
};

describe("registro manual de Caja", () => {
  it("conserva la caja, el contenido y la clave al reenviar", async () => {
    const api = vi.fn().mockResolvedValue(movimiento);
    await registrarCaja(api, intento);
    await registrarCaja(api, intento);
    expect(api).toHaveBeenCalledTimes(2);
    for (const [ruta, opciones] of api.mock.calls) {
      expect(ruta).toBe("/movimientos-caja/ingresos-manuales");
      expect(opciones).toMatchObject({
        body: { ...intento.body, cajaId: intento.cajaId },
        idempotencyKey: intento.clave,
      });
    }
  });

  it("mantiene incierto un 201 que corresponde a otra caja", async () => {
    const api = vi.fn().mockResolvedValue({ ...movimiento, cajaId: 5 });
    await expect(registrarCaja(api, intento)).rejects.toMatchObject({
      status: 201,
      message: "El servidor devolvió una confirmación de caja inválida.",
    });
  });
});
