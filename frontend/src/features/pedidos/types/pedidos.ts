import type { ModalidadCobro } from "@/features/servicios/types/servicios";

type DatosPedido = {
  id: number;
  clienteId: number;
  clienteNombre: string;
  clienteApellido: string;
  fechaIngreso: string;
  fechaEntregaEstimada: string;
  total: number;
};
export type PedidoResumen = DatosPedido & { estadoCodigo: string };
export type EstadoPedido = { id: number; codigo: string; nombre: string };
export type EstadoDisponible = EstadoPedido & {
  descripcion: string | null;
  orden: number;
  activo: boolean;
};
export type Pedido = DatosPedido & {
  usuarioId: number;
  usuarioNombre: string;
  estadoPedido: EstadoPedido;
  detalles: DetallePedido[];
};
export type DetallePedido = {
  id: number;
  prendaId: number;
  prendaNombre: string;
  servicioId: number;
  servicioNombre: string;
  modalidadCobro: ModalidadCobro;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
  estadoPrenda: string | null;
  observaciones: string | null;
};
export type EventoPedido = {
  id: number;
  estadoAnteriorCodigo: string | null;
  estadoAnteriorNombre: string | null;
  estadoNuevoCodigo: string;
  estadoNuevoNombre: string;
  usuarioId: number;
  usuarioNombre: string;
  fechaCambio: string;
  observacion: string | null;
};
export type SaldoPedido = {
  pedidoId: number;
  totalPedido: number;
  totalCobrado: number;
  saldoPendiente: number;
  estadoPagoCodigo: string;
  estadoPagoNombre: string;
};
export type FiltrosPedido = {
  page: number;
  size: number;
  sort: string;
  clienteId?: string;
  estado?: string;
  desde?: string;
  hasta?: string;
};
