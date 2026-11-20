import { z } from "zod";
import { ApiError } from "@/shared/api/http";
import type { ApiClient, PagedResponse } from "@/shared/api/http";
import type { Cobro, IntentoCobro } from "../types/cobros";

const id = z.number().int().positive();
const recibo = z.object({
  id,
  pedidoId: id,
  movimientoCajaId: id,
  cajaId: id,
  usuarioId: id,
  metodoPagoCodigo: z.string(),
  metodoPagoNombre: z.string(),
  usuarioNombre: z.string(),
  fechaCobro: z.iso.datetime({ local: true }),
  montoCobrado: z.number().positive(),
});

const listado = z.object({
  content: z.array(recibo),
  page: z.object({
    size: z.number().int().nonnegative(),
    number: z.number().int().nonnegative(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  }),
});

function validarRespuesta<T>(
  schema: z.ZodType<T>,
  data: unknown,
  status: number,
) {
  const respuesta = schema.safeParse(data);
  if (!respuesta.success) {
    throw new ApiError(
      status,
      "El servidor devolvió una respuesta de cobros inválida.",
    );
  }
  return respuesta.data;
}

export const cobrosApi = {
  async listar(
    api: ApiClient,
    id: string,
    page: number,
    signal: AbortSignal,
  ): Promise<PagedResponse<Cobro>> {
    const data = await api<unknown>(`/pedidos/${id}/cobros`, {
      signal,
      params: { page, size: 20 },
    });
    return validarRespuesta(listado, data, 200);
  },
  async detalle(
    api: ApiClient,
    id: string,
    signal: AbortSignal,
  ): Promise<Cobro> {
    return validarRespuesta(
      recibo,
      await api<unknown>(`/cobros/${id}`, { signal }),
      200,
    );
  },
  async registrar(api: ApiClient, intento: IntentoCobro): Promise<Cobro> {
    const { pedidoId, clave, body } = intento;
    return validarRespuesta(
      recibo,
      await api<unknown>(`/pedidos/${pedidoId}/cobros`, {
        method: "POST",
        body,
        idempotencyKey: clave,
        expectedStatus: 201,
        signal: AbortSignal.timeout(15_000),
      }),
      201,
    );
  },
};
