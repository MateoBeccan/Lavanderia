import type { PagoAsociado } from "@/features/compras/types/compras";

export type SolicitudPagoProveedor = { monto: number; metodoPagoId: number };
export type IntentoPago = Readonly<{
  compraId: string;
  cajaId: number;
  clave: string;
  body: Readonly<SolicitudPagoProveedor>;
}>;

export type OperacionPago =
  | { estado: "LISTA" }
  | { estado: "ENVIANDO"; intento: IntentoPago }
  | {
      estado: "PENDIENTE";
      intento: IntentoPago;
      mensaje: string;
      retryAt: number;
    }
  | { estado: "VERIFICAR"; compraId: string; mensaje: string }
  | { estado: "ERROR"; compraId: string; mensaje: string }
  | { estado: "CONFIRMADA"; pago: PagoAsociado };
