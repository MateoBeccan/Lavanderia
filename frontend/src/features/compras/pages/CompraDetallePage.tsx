import { Link, useLocation } from "react-router";
import { ComprobantesAsociados } from "@/features/comprobantes/components/ComprobantesAsociados";
import {
  puedeGestionarPagos,
  tieneSaldoPendiente,
} from "@/features/pagos-proveedor/schemas/pago";
import { useSession } from "@/features/auth/session";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { PageHeader } from "@/shared/components/PageHeader";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { PagosAsociados } from "../components/PagosAsociados";
import {
  compraEditable,
  fechaCompra,
  importeCompra,
  puedeGestionarCompras,
} from "../components/presentacion";
import { useCompra, useSaldoCompra } from "../hooks/queries";

export function CompraDetallePage({ id }: { id: string }) {
  const { user } = useSession();
  const { state } = useLocation();
  const consulta = useCompra(id);
  const saldo = useSaldoCompra(id);
  const compra = consulta.data;
  const finanzas = saldo.data;
  return (
    <div className="space-y-5">
      <PageHeader
        title={`Compra #${id}`}
        action={
          <div className="flex flex-wrap gap-2">
            {consulta.isSuccess &&
              saldo.isSuccess &&
              tieneSaldoPendiente(saldo.data) &&
              puedeGestionarPagos(user?.rol) && (
                <Button asChild>
                  <Link to={`/pagos-proveedor/compras/${id}/registrar`}>
                    Registrar pago
                  </Link>
                </Button>
              )}
            {compra &&
              !saldo.isError &&
              compraEditable(compra, finanzas) &&
              puedeGestionarCompras(user?.rol) && (
                <Button asChild>
                  <Link to={`/compras/${id}/editar`}>Editar compra</Link>
                </Button>
              )}
            <Button asChild variant="outline">
              <Link to="/compras">Volver a compras</Link>
            </Button>
          </div>
        }
      />
      {state?.guardada && (
        <p role="status" className="text-sm font-medium">
          {state.guardada}
        </p>
      )}
      {consulta.isPending && <LoadingState label="Cargando compra…" />}
      {consulta.isError && (
        <ErrorState
          message={consulta.error.message}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {compra && (
        <>
          <section className="surface grid gap-5 p-5 md:grid-cols-2">
            <dl className="grid gap-3 sm:grid-cols-2">
              <div>
                <dt>Proveedor</dt>
                <dd>
                  <Link
                    className="underline"
                    to={`/proveedores/${compra.proveedorId}`}
                  >
                    {compra.proveedorNombre}
                  </Link>
                </dd>
              </div>
              <div>
                <dt>Fecha</dt>
                <dd>{fechaCompra(compra.fechaCompra)}</dd>
              </div>
              <div>
                <dt>Comprobante</dt>
                <dd>{compra.numeroComprobante || "—"}</dd>
              </div>
              <div>
                <dt>Registrada por</dt>
                <dd>{compra.usuarioNombre}</dd>
              </div>
            </dl>
            <dl className="grid gap-3 border-t pt-4 sm:grid-cols-2 md:border-l md:border-t-0 md:pl-5 md:pt-0">
              <div>
                <dt>Total confirmado</dt>
                <dd className="text-2xl font-bold tabular-nums">
                  {importeCompra(
                    saldo.isSuccess
                      ? saldo.data.totalCompra
                      : compra.totalCompra,
                  )}
                </dd>
              </div>
              <div>
                <dt>Estado de pago</dt>
                <dd>
                  {(!saldo.isError && finanzas?.estadoPagoNombre) ||
                    compra.estadoPagoNombre}
                </dd>
              </div>
              {saldo.isPending && (
                <div className="col-span-full">
                  <LoadingState label="Cargando saldo…" />
                </div>
              )}
              {saldo.isError && (
                <div className="col-span-full">
                  <ErrorState
                    message={saldo.error.message}
                    onRetry={() => void saldo.refetch()}
                  />
                </div>
              )}
              {finanzas && !saldo.isError && (
                <>
                  <div>
                    <dt>Total pagado</dt>
                    <dd>{importeCompra(finanzas.totalPagado)}</dd>
                  </div>
                  <div>
                    <dt>Saldo pendiente</dt>
                    <dd className="font-semibold">
                      {importeCompra(finanzas.saldoPendiente)}
                    </dd>
                  </div>
                </>
              )}
            </dl>
          </section>
          <DataTable
            caption="Renglones de la compra"
            data={compra.detalles}
            getRowId={(fila) => String(fila.id)}
            columns={[
              { accessorKey: "insumoNombre", header: "Insumo" },
              {
                id: "cantidad",
                header: "Cantidad",
                cell: ({ row }) => importeCompra(row.original.cantidad),
              },
              {
                id: "precio",
                header: "Precio de compra",
                cell: ({ row }) => importeCompra(row.original.precioCompra),
              },
              {
                id: "subtotal",
                header: "Subtotal",
                cell: ({ row }) => importeCompra(row.original.subtotal),
              },
            ]}
          />
          <PagosAsociados id={id} />
          <ComprobantesAsociados key={id} padre="compras" id={id} />
        </>
      )}
    </div>
  );
}
