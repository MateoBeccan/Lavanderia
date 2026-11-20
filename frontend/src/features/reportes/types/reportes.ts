export type TipoReporte = "pedidos" | "movimientos-caja" | "compras";

export type CampoFiltro =
  | "fechaDesde"
  | "fechaHasta"
  | "clienteId"
  | "proveedorId"
  | "estadoPedidoCodigo"
  | "estadoPagoCodigo"
  | "tipoMovimientoCodigo"
  | "metodoPagoCodigo";

export type Filtros = Partial<Record<CampoFiltro, string>>;

export type PaginaReporte<Fila, Resumen> = {
  datos: Fila[];
  resumen: Resumen;
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
};

export type PedidoReportado = {
  pedidoId: string;
  fechaIngreso: string;
  fechaEntregaEstimada: string | null;
  clienteId: string;
  clienteNombreCompleto: string;
  estadoPedidoCodigo: string;
  estadoPedidoNombre: string;
  estadoPagoCodigo: string | null;
  estadoPagoNombre: string | null;
  totalPedido: string;
};

export type MovimientoReportado = {
  movimientoId: string;
  cajaId: string;
  fechaHora: string;
  tipoMovimientoCodigo: string;
  tipoMovimientoNombre: string;
  metodoPagoCodigo: string;
  metodoPagoNombre: string;
  usuarioId: string;
  usuarioNombre: string;
  descripcion: string | null;
  monto: string;
  naturaleza: "INGRESO" | "EGRESO";
};

export type CompraReportada = {
  compraId: string;
  fechaCompra: string;
  proveedorId: string;
  proveedorNombre: string;
  numeroComprobante: string | null;
  estadoPagoCodigo: string;
  estadoPagoNombre: string;
  totalCompra: string;
};

export type Filas = {
  pedidos: PedidoReportado;
  "movimientos-caja": MovimientoReportado;
  compras: CompraReportada;
};

export type Resumenes = {
  pedidos: { cantidadPedidos: number; totalGeneral: string };
  "movimientos-caja": {
    cantidadMovimientos: number;
    totalIngresos: string;
    totalEgresos: string;
    saldoNeto: string;
  };
  compras: { cantidadCompras: number; totalComprado: string };
};
