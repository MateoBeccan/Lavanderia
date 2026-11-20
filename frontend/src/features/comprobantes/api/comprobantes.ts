import { ApiError, type ApiClient } from "@/shared/api/http";
import {
  detalleComprobanteSchema,
  paginaComprobantesSchema,
} from "../schemas/comprobante";
import type {
  Comprobante,
  OperacionComprobante,
  PadreComprobantes,
  ReferenciaComprobante,
  SolicitudComprobante,
} from "../types/comprobantes";

function rutaOperacion({ tipo, id }: OperacionComprobante) {
  const recurso = tipo === "COBRO" ? "cobros" : "pagos-proveedor";
  return `/${recurso}/${id}/comprobante`;
}

function validarDetalle(
  data: unknown,
  status: number,
  coincide: (comprobante: Comprobante) => boolean,
) {
  const resultado = detalleComprobanteSchema.safeParse(data);
  if (resultado.success && coincide(resultado.data)) return resultado.data;
  throw new ApiError(status, "Spring devolvió un comprobante inválido.");
}

function coincideOperacion(
  comprobante: Comprobante,
  { tipo, id, padreId }: ReferenciaComprobante,
) {
  return (
    tipo === comprobante.tipoOperacion &&
    (tipo === "COBRO"
      ? String(comprobante.cobroId) === id && comprobante.pedidoId === padreId
      : String(comprobante.pagoProveedorId) === id &&
        comprobante.compraId === padreId)
  );
}

export const comprobantesApi = {
  async listar(
    api: ApiClient,
    padre: PadreComprobantes,
    id: string,
    page: number,
    signal: AbortSignal,
  ) {
    const data = await api<unknown>(`/${padre}/${id}/comprobantes`, {
      params: { page, size: 20 },
      signal,
    });
    const resultado = paginaComprobantesSchema.safeParse(data);
    if (!resultado.success) {
      throw new ApiError(
        200,
        "Spring devolvió un listado de comprobantes inválido.",
      );
    }
    return resultado.data;
  },
  async detalle(api: ApiClient, id: string, signal: AbortSignal) {
    const data = await api<unknown>(`/comprobantes/${id}`, { signal });
    return validarDetalle(
      data,
      200,
      (comprobante) => String(comprobante.id) === id,
    );
  },
  async deOperacion(
    api: ApiClient,
    operacion: ReferenciaComprobante,
    signal: AbortSignal,
  ) {
    try {
      const data = await api<unknown>(rutaOperacion(operacion), { signal });
      return validarDetalle(data, 200, (comprobante) =>
        coincideOperacion(comprobante, operacion),
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  },
  async registrar(
    api: ApiClient,
    operacion: ReferenciaComprobante,
    body: SolicitudComprobante,
  ) {
    const data = await api<unknown>(rutaOperacion(operacion), {
      method: "POST",
      expectedStatus: 201,
      signal: AbortSignal.timeout(15_000),
      body,
    });
    return validarDetalle(
      data,
      201,
      (comprobante) =>
        coincideOperacion(comprobante, operacion) &&
        comprobante.fechaEmision === body.fechaEmision,
    );
  },
};
