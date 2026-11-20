export type EstadoCaja = "ABIERTA" | "CERRADA";
export type CajaResumen = {
  id: number;
  estado: EstadoCaja;
  fechaApertura: string;
  fechaCierre: string | null;
  montoInicial: number;
  montoFinalCalculado: number | null;
  diferenciaArqueo: number | null;
};
export type Caja = CajaResumen & {
  usuarioAperturaId: number;
  usuarioAperturaNombre: string;
  usuarioCierreId: number | null;
  usuarioCierreNombre: string | null;
  efectivoEsperado: number | null;
  efectivoContado: number | null;
};
export type SaldoCaja = Omit<Caja, "id" | "estado" | "montoFinalCalculado"> & {
  cajaId: number;
  estadoCaja: EstadoCaja;
  totalIngresos: number;
  totalEgresos: number;
  saldoGeneral: number;
  ingresosEfectivo: number;
  egresosEfectivo: number;
  efectivoDisponible: number;
};
export type MovimientoCaja = {
  id: number;
  cajaId: number;
  tipoMovimientoCodigo: string;
  tipoMovimientoNombre: string;
  metodoPagoCodigo: string;
  metodoPagoNombre: string;
  usuarioId: number;
  usuarioNombre: string;
  monto: number;
  descripcion: string;
  fechaHora: string;
};
export type OpcionCaja = { id: number; codigo: string; nombre: string };
export type FiltrosMovimientos = {
  tipo?: string;
  metodoPagoId?: string;
  desde?: string;
  hasta?: string;
};
