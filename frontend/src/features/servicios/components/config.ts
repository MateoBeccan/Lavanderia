import type { ConfigCatalogo } from "@/shared/catalogos/config";
import { serviciosApi } from "../api/servicios";
import { servicioSchema } from "../schemas/servicio";
import { modalidadesCobro } from "../types/servicios";
import type { ServicioRequest, ServicioResponse } from "../types/servicios";

const decimal = new Intl.NumberFormat("es-AR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
export const configServicio: ConfigCatalogo<ServicioRequest, ServicioResponse> =
  {
    api: serviciosApi,
    schema: servicioSchema,
    titulo: "Servicios",
    singular: "servicio",
    genero: "o",
    modalidades: modalidadesCobro,
    valores: (registro) => ({
      nombre: registro?.nombre ?? "",
      precio: registro ? String(registro.precio) : "",
      modalidadCobro: registro?.modalidadCobro ?? "",
    }),
    datos: [
      { label: "Precio", valor: (registro) => decimal.format(registro.precio) },
      {
        label: "Modalidad de cobro",
        valor: (registro) => registro.modalidadCobro,
      },
    ],
  };
