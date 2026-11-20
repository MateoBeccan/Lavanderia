import { z } from "zod";
import { ApiError } from "@/shared/api/http";
import type { ApiClient } from "@/shared/api/http";
import type { aperturaSchema, movimientoManualSchema } from "../schemas/caja";

export type TipoManual = "INGRESO_MANUAL" | "EGRESO_MANUAL";
export type SolicitudCaja =
  | { tipo: "APERTURA"; body: z.output<typeof aperturaSchema> }
  | {
      tipo: TipoManual;
      cajaId: number;
      body: z.output<typeof movimientoManualSchema>;
    };
export type IntentoCaja = Readonly<SolicitudCaja & { clave: string }>;

const id = z.number().int().positive();
const apertura = z.object({
  id,
  montoInicial: z.number().nonnegative(),
  estado: z.literal("ABIERTA"),
});
const movimiento = z.object({
  id,
  cajaId: id,
  monto: z.number().positive(),
  descripcion: z.string(),
  metodoPagoNombre: z.string(),
  tipoMovimientoCodigo: z.enum(["INGRESO_MANUAL", "EGRESO_MANUAL"]),
});

const rutas = {
  APERTURA: "/cajas/abrir",
  INGRESO_MANUAL: "/movimientos-caja/ingresos-manuales",
  EGRESO_MANUAL: "/movimientos-caja/egresos-manuales",
} as const;

export async function registrarCaja(api: ApiClient, intento: IntentoCaja) {
  const data = await api<unknown>(rutas[intento.tipo], {
    method: "POST",
    body:
      intento.tipo === "APERTURA"
        ? intento.body
        : { ...intento.body, cajaId: intento.cajaId },
    idempotencyKey: intento.clave,
    expectedStatus: 201,
    signal: AbortSignal.timeout(15_000),
  });
  if (intento.tipo === "APERTURA") {
    const respuesta = apertura.safeParse(data);
    if (respuesta.success) return { tipo: intento.tipo, caja: respuesta.data };
  } else {
    const respuesta = movimiento.safeParse(data);
    if (
      respuesta.success &&
      respuesta.data.tipoMovimientoCodigo === intento.tipo &&
      respuesta.data.cajaId === intento.cajaId
    )
      return { tipo: intento.tipo, movimiento: respuesta.data };
  }
  throw new ApiError(
    201,
    "El servidor devolvió una confirmación de caja inválida.",
  );
}
