export type TipoOperacion = "COBRO" | "PAGO_PROVEEDOR";

export type OperacionComprobante = {
  tipo: TipoOperacion;
  id: string;
};

export type ReferenciaComprobante = OperacionComprobante & {
  padreId: number;
  monto: number;
};

export type PadreComprobantes = "pedidos" | "compras";

export type SolicitudComprobante = {
  fechaEmision: string;
  observaciones: string | null;
};

export type ComprobanteResumen = {
  id: number;
  tipoOperacion: TipoOperacion;
  fechaEmision: string;
  montoOperacion: number;
  observaciones: string | null;
};

export type Comprobante = ComprobanteResumen & {
  metodoPagoCodigo: string;
  metodoPagoNombre: string;
  movimientoCajaId: number;
} & (
    | {
        tipoOperacion: "COBRO";
        cobroId: number;
        pedidoId: number;
        pagoProveedorId: null;
        compraId: null;
      }
    | {
        tipoOperacion: "PAGO_PROVEEDOR";
        cobroId: null;
        pedidoId: null;
        pagoProveedorId: number;
        compraId: number;
      }
  );
