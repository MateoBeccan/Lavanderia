import type { ApiClient } from "@/shared/api/http";
import type {
  Filas,
  Filtros,
  PaginaReporte,
  Resumenes,
  TipoReporte,
} from "../types/reportes";

const camposMonetarios = new Set([
  "totalPedido",
  "totalGeneral",
  "monto",
  "totalIngresos",
  "totalEgresos",
  "saldoNeto",
  "totalCompra",
  "totalComprado",
]);
const identificadores = new Set([
  "pedidoId",
  "clienteId",
  "compraId",
  "proveedorId",
  "movimientoId",
  "cajaId",
  "usuarioId",
]);

export function decodificarReporte(body: string): unknown {
  // Se recorren tokens de cadena completos para preservar texto escapado en las filas.
  // Se conservan los BigDecimal y los identificadores Long antes de convertir el resto del JSON.
  const json = body.replace(
    /("(?:[^"\\]|\\.)*")(?:(\s*:\s*)(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?))?/g,
    (token: string, clave: string, separador?: string, numero?: string) => {
      if (!separador || !numero) return token;
      const campo = JSON.parse(clave) as string;
      if (camposMonetarios.has(campo)) {
        if (!/^-?\d+(?:\.\d{1,2})?$/.test(numero))
          throw new Error("Importe inválido.");
      } else if (identificadores.has(campo)) {
        if (!/^[1-9]\d*$/.test(numero))
          throw new Error("Identificador inválido.");
      } else {
        return token;
      }
      return clave + separador + JSON.stringify(numero);
    },
  );
  return JSON.parse(json) as unknown;
}

export function consultarReporte<T extends TipoReporte>(
  api: ApiClient,
  tipo: T,
  params: Filtros & { page: number; size: number },
  signal?: AbortSignal,
) {
  return api<PaginaReporte<Filas[T], Resumenes[T]>>(`/reportes/${tipo}`, {
    params,
    signal,
    decodeJson: decodificarReporte,
  });
}

export function descargarReporte(
  api: ApiClient,
  tipo: TipoReporte,
  filtros: Filtros,
  signal: AbortSignal,
) {
  return api<{ blob: Blob; nombre?: string }>(`/reportes/${tipo}/pdf`, {
    params: filtros,
    signal,
    responseType: "pdf",
    expectedStatus: 200,
  });
}
