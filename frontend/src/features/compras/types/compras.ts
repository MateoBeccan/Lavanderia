export type CompraResumen = {
  id: number;
  proveedorId: number;
  proveedorNombre: string;
  fechaCompra: string;
  numeroComprobante: string | null;
  totalCompra: number;
  estadoPagoCodigo: string;
};

export type DetalleCompra = {
  id: number;
  insumoId: number;
  insumoNombre: string;
  cantidad: number;
  precioCompra: number;
  subtotal: number;
};

export type Compra = CompraResumen & {
  usuarioId: number;
  usuarioNombre: string;
  estadoPagoNombre: string;
  detalles: DetalleCompra[];
};

export type SaldoCompra = Pick<
  Compra,
  "totalCompra" | "estadoPagoCodigo" | "estadoPagoNombre"
> & {
  compraId: number;
  totalPagado: number;
  saldoPendiente: number;
};

export type PagoAsociado = {
  id: number;
  compraId: number;
  proveedorId: number;
  proveedorNombre: string;
  movimientoCajaId: number;
  cajaId: number;
  metodoPagoCodigo: string;
  metodoPagoNombre: string;
  usuarioId: number;
  usuarioNombre: string;
  fechaPago: string;
  montoPagado: number;
};

export type EstadoPago = {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  orden: number;
  activo: boolean;
};

export type CompraFiltros = {
  proveedorId?: string;
  fechaDesde?: string;
  fechaHasta?: string;
  estadoPagoCodigo?: string;
  numeroComprobante?: string;
};

export type CompraRequest = {
  proveedorId: number;
  numeroComprobante: string | null;
  detalles: {
    detalleId?: number;
    insumoId: number;
    cantidad: string;
    precioCompra: string;
  }[];
};
