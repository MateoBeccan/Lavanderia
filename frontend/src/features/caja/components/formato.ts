const dinero = new Intl.NumberFormat("es-AR", {
  style: "currency",
  currency: "ARS",
});
const fechaLocal = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short",
  timeStyle: "medium",
  timeZone: "UTC",
});
const consulta = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short",
  timeStyle: "medium",
  timeZone: "America/Argentina/Buenos_Aires",
});
export const importeCaja = (monto: number) => dinero.format(monto);
// Spring entrega LocalDateTime. Se conservan los campos de la hora comercial.
export const fechaCaja = (iso: string) =>
  fechaLocal.format(new Date(iso + "Z"));
export const fechaConsulta = (timestamp: number) => consulta.format(timestamp);

export function naturaleza(codigo: string) {
  switch (codigo) {
    case "INGRESO_MANUAL":
    case "COBRO_PEDIDO":
      return "Ingreso";
    case "EGRESO_MANUAL":
    case "PAGO_PROVEEDOR":
      return "Egreso";
    default:
      return undefined;
  }
}
