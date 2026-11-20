import { ReceiptText, ShoppingCart } from "lucide-react";
import { useEstadosPago } from "@/features/compras/hooks/queries";
import { fechaCompra } from "@/features/compras/components/presentacion";
import { StatCard } from "@/shared/components/StatCard";
import { ConsultaReporte, EnlaceRegistro } from "../components/ConsultaReporte";
import { cantidadReporte, importeReporte } from "../components/formato";

export function ReporteCompras() {
  const estados = useEstadosPago();
  return (
    <ConsultaReporte
      tipo="compras"
      titulo="Compras del reporte"
      nota="El total corresponde al importe de las compras. Consultá pagos a proveedores y saldo en cada ficha."
      campos={[
        { nombre: "proveedorId", etiqueta: "ID del proveedor" },
        {
          nombre: "estadoPagoCodigo",
          etiqueta: "Estado de pago",
          catalogo: estados,
        },
      ]}
      indicadores={(resumen) => (
        <>
          <StatCard title="Cantidad de compras" icon={ShoppingCart}>
            {cantidadReporte(resumen.cantidadCompras)}
          </StatCard>
          <StatCard title="Importe total de compras" icon={ReceiptText}>
            {importeReporte(resumen.totalComprado)}
          </StatCard>
        </>
      )}
      getRowId={(fila) => String(fila.compraId)}
      columns={[
        {
          header: "Compra",
          id: "compra",
          cell: ({
            row: {
              original: { compraId },
            },
          }) => (
            <EnlaceRegistro to={`/compras/${compraId}`}>
              Compra #{compraId}
            </EnlaceRegistro>
          ),
        },
        { header: "Proveedor", accessorKey: "proveedorNombre" },
        {
          header: "Fecha de compra",
          id: "fecha",
          cell: ({ row }) => fechaCompra(row.original.fechaCompra),
        },
        {
          header: "Comprobante",
          id: "comprobante",
          cell: ({ row }) => row.original.numeroComprobante ?? "—",
        },
        { header: "Estado de pago", accessorKey: "estadoPagoNombre" },
        {
          header: "Importe de la compra",
          id: "total",
          cell: ({ row }) => importeReporte(row.original.totalCompra),
        },
      ]}
    />
  );
}
