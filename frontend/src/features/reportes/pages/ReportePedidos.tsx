import { ClipboardList, ReceiptText } from "lucide-react";
import { useEstadosPago } from "@/features/compras/hooks/queries";
import { useEstadosPedido } from "@/features/pedidos/hooks/queries";
import { PedidoEstadoBadge } from "@/features/pedidos/components/PedidoEstadoBadge";
import { fechaPedido } from "@/features/pedidos/rules";
import { StatCard } from "@/shared/components/StatCard";
import { ConsultaReporte, EnlaceRegistro } from "../components/ConsultaReporte";
import { cantidadReporte, importeReporte } from "../components/formato";

export function ReportePedidos() {
  const estados = useEstadosPedido();
  const pagos = useEstadosPago();
  return (
    <ConsultaReporte
      tipo="pedidos"
      titulo="Pedidos del reporte"
      nota="El total corresponde al importe de los pedidos. Consultá cobros y saldo en cada ficha."
      campos={[
        { nombre: "clienteId", etiqueta: "ID del cliente" },
        {
          nombre: "estadoPedidoCodigo",
          etiqueta: "Estado del pedido",
          catalogo: estados,
        },
        {
          nombre: "estadoPagoCodigo",
          etiqueta: "Estado de pago",
          catalogo: pagos,
        },
      ]}
      indicadores={(resumen) => (
        <>
          <StatCard title="Cantidad de pedidos" icon={ClipboardList}>
            {cantidadReporte(resumen.cantidadPedidos)}
          </StatCard>
          <StatCard title="Importe total de pedidos" icon={ReceiptText}>
            {importeReporte(resumen.totalGeneral)}
          </StatCard>
        </>
      )}
      getRowId={(fila) => String(fila.pedidoId)}
      columns={[
        {
          header: "Pedido",
          id: "pedido",
          cell: ({
            row: {
              original: { pedidoId },
            },
          }) => (
            <EnlaceRegistro to={`/pedidos/${pedidoId}`}>
              Pedido #{pedidoId}
            </EnlaceRegistro>
          ),
        },
        { header: "Cliente", accessorKey: "clienteNombreCompleto" },
        {
          header: "Ingreso",
          id: "ingreso",
          cell: ({ row }) => fechaPedido(row.original.fechaIngreso, true),
        },
        {
          header: "Entrega estimada",
          id: "entrega",
          cell: ({ row }) =>
            row.original.fechaEntregaEstimada
              ? fechaPedido(row.original.fechaEntregaEstimada)
              : "—",
        },
        {
          header: "Estado del pedido",
          id: "estado",
          cell: ({ row }) => (
            <PedidoEstadoBadge
              codigo={row.original.estadoPedidoCodigo}
              nombre={row.original.estadoPedidoNombre}
            />
          ),
        },
        {
          header: "Estado de pago",
          id: "estadoPago",
          cell: ({ row }) => row.original.estadoPagoNombre ?? "Sin estado",
        },
        {
          header: "Importe del pedido",
          id: "importe",
          cell: ({ row }) => importeReporte(row.original.totalPedido),
        },
      ]}
    />
  );
}
