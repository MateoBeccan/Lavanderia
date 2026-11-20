const moneda = new Intl.NumberFormat("es-AR", {
  style: "currency",
  currency: "ARS",
});
const entero = new Intl.NumberFormat("es-AR");

export const cantidadReporte = (cantidad: number) => entero.format(cantidad);

export function importeReporte(importe: string) {
  const [unidades, decimales = ""] = importe.split(".");
  const valor = unidades === "-0" ? -0 : BigInt(unidades);
  return moneda
    .formatToParts(valor)
    .map(({ type, value }) =>
      type === "fraction" ? decimales.padEnd(2, "0") : value,
    )
    .join("");
}
