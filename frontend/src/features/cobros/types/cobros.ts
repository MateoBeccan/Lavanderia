export type Cobro = {
  id: number;
  pedidoId: number;
  movimientoCajaId: number;
  cajaId: number;
  metodoPagoCodigo: string;
  metodoPagoNombre: string;
  usuarioId: number;
  usuarioNombre: string;
  fechaCobro: string;
  montoCobrado: number;
};

export type SolicitudCobro = Readonly<{ monto: number; metodoPagoId: number }>;
export type IntentoCobro = Readonly<{
  pedidoId: string;
  clave: string;
  body: SolicitudCobro;
}>;
