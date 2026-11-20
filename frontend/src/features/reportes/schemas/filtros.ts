import { idPedidoValido } from "@/features/pedidos/rules";
import type { CampoFiltro, Filtros } from "../types/reportes";

export const fechasReporte = ["fechaDesde", "fechaHasta"] as const;

export function leerFiltros(
  origen: URLSearchParams | FormData,
  campos: readonly CampoFiltro[],
): Filtros {
  const filtros: Filtros = {};
  for (const campo of campos) {
    const valor = String(origen.get(campo) ?? "").trim();
    if (valor) filtros[campo] = valor;
  }
  return filtros;
}

function fechaValida(fecha: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha) || fecha.startsWith("0000"))
    return false;
  const fechaISO = new Date(fecha + "T00:00:00Z");
  return (
    Number.isFinite(fechaISO.getTime()) &&
    fechaISO.toISOString().slice(0, 10) === fecha
  );
}

export function validarFiltros({
  fechaDesde,
  fechaHasta,
  clienteId,
  proveedorId,
  ...codigos
}: Filtros) {
  if (fechaDesde && !fechaValida(fechaDesde))
    return "Ingresá una fecha desde válida.";
  if (fechaHasta && !fechaValida(fechaHasta))
    return "Ingresá una fecha hasta válida.";
  if (fechaDesde && fechaHasta && fechaDesde > fechaHasta)
    return "La fecha desde debe ser anterior o igual a la fecha hasta.";
  if (clienteId && !idPedidoValido(clienteId))
    return "Ingresá un ID de cliente válido.";
  if (proveedorId && !idPedidoValido(proveedorId))
    return "Ingresá un ID de proveedor válido.";
  if (Object.values(codigos).some((codigo) => codigo.length > 30))
    return "Revisá los códigos de los filtros.";
  return undefined;
}

export function leerPaginacion(params: URLSearchParams) {
  const entero = (
    campo: string,
    minimo: number,
    maximo: number,
    predeterminado: number,
  ) => {
    const valor = params.get(campo) ?? "";
    const numero = Number(valor);
    return /^\d+$/.test(valor) && numero >= minimo && numero <= maximo
      ? numero
      : predeterminado;
  };
  return {
    page: entero("page", 0, 2_147_483_647, 0),
    size: entero("size", 1, 100, 20),
  };
}
