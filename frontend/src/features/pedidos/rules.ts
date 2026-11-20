import { ApiError } from "@/shared/api/http";
import type { EstadoDisponible } from "./types/pedidos";
import type { Role } from "@/features/auth/contracts";

// El catálogo de estados activos no expone el grafo de TransicionesPedido.
const transiciones: Readonly<Record<string, readonly string[]>> = {
  PENDIENTE: ["EN_PROCESO", "CANCELADO"],
  EN_PROCESO: ["LISTO", "CANCELADO"],
  LISTO: ["ENTREGADO"],
};

export const puedeOperarPedidos = (rol: Role) =>
  rol === "ADMINISTRADOR" || rol === "EMPLEADO";
export const estadoEditable = (codigo: string) =>
  codigo !== "ENTREGADO" && codigo !== "CANCELADO";

export function destinosPedido(
  actual: string,
  estados: EstadoDisponible[],
  tieneCobros?: boolean,
) {
  return estados.filter(
    ({ activo, codigo }) =>
      activo &&
      (transiciones[actual] ?? []).includes(codigo) &&
      (codigo !== "CANCELADO" || tieneCobros === false),
  );
}
const moneda = new Intl.NumberFormat("es-AR", {
  style: "currency",
  currency: "ARS",
});
export const importePedido = (importe: number) => moneda.format(importe);
const fecha = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short",
  timeZone: "UTC",
});
const fechaHora = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "UTC",
});
export const fechaPedido = (iso: string, conHora = false) =>
  (conHora ? fechaHora : fecha).format(new Date(iso + (conHora ? "Z" : "")));
export const hoyPedidos = () =>
  new Intl.DateTimeFormat("sv-SE", {
    timeZone: "America/Argentina/Buenos_Aires",
  }).format();

export const idPedidoValido = (id: string) =>
  /^[1-9][0-9]{0,18}$/.test(id) && BigInt(id) <= 9223372036854775807n;

export const errorPedido = (error: unknown) =>
  error instanceof ApiError
    ? error.message
    : "No pudimos completar la operación.";

export const resultadoIncierto = (error: unknown) =>
  !(error instanceof ApiError) ||
  error.status === 0 ||
  error.status === 408 ||
  error.status >= 500 ||
  (error.status >= 200 && error.status < 300);
