import { z } from "zod";
import { ApiError } from "@/shared/api/http";
import type { ApiClient } from "@/shared/api/http";
import type { IntentoPago } from "../types/pagos";

const id = z.number().int().positive();
const pago = z.object({
  id,
  compraId: id,
  proveedorId: id,
  proveedorNombre: z.string(),
  movimientoCajaId: id,
  cajaId: id,
  metodoPagoCodigo: z.string(),
  metodoPagoNombre: z.string(),
  usuarioId: id,
  usuarioNombre: z.string(),
  fechaPago: z.iso.datetime({ local: true }),
  montoPagado: z.number().positive(),
});

function validar(
  data: unknown,
  status: number,
  coincide: (p: z.infer<typeof pago>) => boolean,
) {
  const resultado = pago.safeParse(data);
  if (resultado.success && coincide(resultado.data)) return resultado.data;
  throw new ApiError(status, "Spring devolvió una respuesta de pago inválida.");
}

export const pagosApi = {
  async registrar(api: ApiClient, { compraId, clave, body }: IntentoPago) {
    const data = await api<unknown>(`/compras/${compraId}/pagos`, {
      method: "POST",
      body,
      idempotencyKey: clave,
      expectedStatus: 201,
      signal: AbortSignal.timeout(15_000),
    });
    return validar(
      data,
      201,
      (p) => String(p.compraId) === compraId && p.montoPagado === body.monto,
    );
  },
  async detalle(api: ApiClient, id: string, signal: AbortSignal) {
    return validar(
      await api<unknown>(`/pagos-proveedor/${id}`, { signal }),
      200,
      (p) => String(p.id) === id,
    );
  },
};
