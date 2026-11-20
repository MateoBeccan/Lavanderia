import type { Role } from "@/features/auth/contracts";
import { ApiError } from "@/shared/api/http";
import type { Compra, SaldoCompra } from "../types/compras";

const decimal = new Intl.NumberFormat("es-AR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const entero = new Intl.NumberFormat("es-AR");
const fechaComercial = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "UTC",
});
export const importeCompra = (valor: number) => decimal.format(valor);
export const fechaCompra = (valor: string) =>
  fechaComercial.format(new Date(valor + "Z"));
export const importeEstimado = (valor: bigint) =>
  entero.format(valor / 100n) + "," + String(valor % 100n).padStart(2, "0");

export function puedeGestionarCompras(rol?: Role) {
  return rol === "ADMINISTRADOR" || rol === "EMPLEADO";
}

export function compraEditable(compra: Compra, saldo?: SaldoCompra) {
  return (
    compra.estadoPagoCodigo === "PENDIENTE" &&
    saldo?.estadoPagoCodigo === "PENDIENTE" &&
    saldo.totalPagado === 0
  );
}

export function resultadoIncierto(error: unknown) {
  return (
    !(error instanceof ApiError) ||
    error.status === 0 ||
    error.status === 408 ||
    (error.status >= 200 && error.status < 300) ||
    error.status >= 500
  );
}
