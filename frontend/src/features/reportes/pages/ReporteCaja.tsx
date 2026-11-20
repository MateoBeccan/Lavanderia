import { ArrowDownLeft, ArrowUpRight, List, Wallet } from "lucide-react";
import { fechaCaja } from "@/features/caja/components/formato";
import { useCatalogosCaja } from "@/features/caja/hooks/queries";
import { StatCard } from "@/shared/components/StatCard";
import { ConsultaReporte, EnlaceRegistro } from "../components/ConsultaReporte";
import { cantidadReporte, importeReporte } from "../components/formato";

export function ReporteCaja() {
  const { tipos, metodos } = useCatalogosCaja();
  return (
    <ConsultaReporte
      tipo="movimientos-caja"
      titulo="Movimientos de Caja del reporte"
      nota="El neto es la diferencia entre ingresos y egresos filtrados. Consultá el saldo disponible y el arqueo en la ficha de cada caja."
      campos={[
        {
          nombre: "tipoMovimientoCodigo",
          etiqueta: "Tipo de movimiento",
          catalogo: tipos,
        },
        {
          nombre: "metodoPagoCodigo",
          etiqueta: "Método de pago",
          catalogo: metodos,
        },
      ]}
      indicadores={(resumen) => (
        <>
          <StatCard title="Cantidad de movimientos" icon={List}>
            {cantidadReporte(resumen.cantidadMovimientos)}
          </StatCard>
          <StatCard title="Ingresos" icon={ArrowDownLeft}>
            {importeReporte(resumen.totalIngresos)}
          </StatCard>
          <StatCard title="Egresos" icon={ArrowUpRight}>
            {importeReporte(resumen.totalEgresos)}
          </StatCard>
          <StatCard title="Neto de movimientos" icon={Wallet}>
            {importeReporte(resumen.saldoNeto)}
          </StatCard>
        </>
      )}
      getRowId={(fila) => String(fila.movimientoId)}
      columns={[
        {
          header: "Movimiento",
          id: "movimiento",
          cell: ({
            row: {
              original: { cajaId, movimientoId },
            },
          }) => (
            <EnlaceRegistro to={`/caja/${cajaId}/movimientos/${movimientoId}`}>
              Movimiento #{movimientoId}
            </EnlaceRegistro>
          ),
        },
        {
          header: "Caja",
          id: "caja",
          cell: ({
            row: {
              original: { cajaId },
            },
          }) => (
            <EnlaceRegistro to={`/caja/${cajaId}`}>
              Caja #{cajaId}
            </EnlaceRegistro>
          ),
        },
        {
          header: "Fecha y hora",
          id: "fecha",
          cell: ({ row }) => fechaCaja(row.original.fechaHora),
        },
        { header: "Tipo de movimiento", accessorKey: "tipoMovimientoNombre" },
        {
          header: "Naturaleza",
          id: "naturaleza",
          cell: ({ row }) =>
            row.original.naturaleza === "INGRESO" ? "Ingreso" : "Egreso",
        },
        { header: "Método de pago", accessorKey: "metodoPagoNombre" },
        {
          header: "Importe",
          id: "monto",
          cell: ({ row }) => importeReporte(row.original.monto),
        },
      ]}
    />
  );
}
