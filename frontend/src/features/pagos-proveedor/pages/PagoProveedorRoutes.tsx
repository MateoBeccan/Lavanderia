import { Link, Route, Routes, useParams, useSearchParams } from "react-router";
import { useState } from "react";
import { useSession } from "@/features/auth/session";
import { CajaFeedback } from "@/features/caja/components/CajaFeedback";
import { ComprobanteOperacion } from "@/features/comprobantes/components/ComprobanteOperacion";
import { CompraFiltros } from "@/features/compras/components/CompraFiltros";
import { PagosAsociados } from "@/features/compras/components/PagosAsociados";
import { fechaCompra } from "@/features/compras/components/presentacion";
import {
  useCompra,
  useCompras,
  useSaldoCompra,
} from "@/features/compras/hooks/queries";
import type { CompraFiltros as Filtros } from "@/features/compras/types/compras";
import { idCatalogoValido } from "@/shared/catalogos/schema";
import { EmptyState, ErrorState } from "@/shared/components/feedback/States";
import { PageHeader } from "@/shared/components/PageHeader";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { PagoProveedorDatos } from "../components/PagoProveedorDatos";
import { SaldoCompra } from "../components/SaldoCompra";
import { usePago } from "../hooks/queries";
import { compraPendiente, useOperacionPago } from "../hooks/useOperacionPago";
import { puedeGestionarPagos, tieneSaldoPendiente } from "../schemas/pago";
import { RegistrarPagoProveedor } from "./RegistrarPagoProveedor";

function SelectorCompras({ proveedorId }: { proveedorId?: string }) {
  const [filtros, aplicar] = useState<Filtros>({ proveedorId });
  const [page, cambiarPagina] = useState(0);
  const compras = useCompras(filtros, page);
  return (
    <section className="grid gap-4" aria-label="Buscar compra">
      <CompraFiltros
        proveedorInicial={proveedorId}
        aplicar={(nuevos) => {
          aplicar(nuevos);
          cambiarPagina(0);
        }}
      />
      <CajaFeedback query={compras} />
      {compras.isSuccess &&
        (compras.data.content.length === 0 && page === 0 ? (
          <EmptyState
            title="Sin compras"
            description="No hay compras para los filtros aplicados."
          />
        ) : (
          <DataTable
            caption="Compras para consultar pagos"
            data={compras.data.content}
            getRowId={(compra) => String(compra.id)}
            pagination={{
              pageIndex: compras.data.page.number,
              totalPages: compras.data.page.totalPages,
              onPageChange: cambiarPagina,
            }}
            columns={[
              {
                id: "compra",
                header: "Compra",
                cell: ({ row }) => (
                  <Link
                    className="underline"
                    to={`/pagos-proveedor?compraId=${row.original.id}`}
                  >
                    Consultar pagos de compra #{row.original.id}
                  </Link>
                ),
              },
              { accessorKey: "proveedorNombre", header: "Proveedor" },
              {
                id: "fecha",
                header: "Fecha de compra",
                cell: ({ row }) => fechaCompra(row.original.fechaCompra),
              },
            ]}
          />
        ))}
    </section>
  );
}

function HistorialCompra({ id }: { id: string }) {
  const compra = useCompra(id);
  const saldo = useSaldoCompra(id);
  return (
    <>
      <CajaFeedback query={compra} />
      <CajaFeedback query={saldo} />
      {compra.isSuccess && (
        <>
          <section
            className="surface grid gap-4 p-4 sm:p-6"
            aria-label="Compra seleccionada"
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold">
                  <Link className="underline" to={`/compras/${id}`}>
                    Compra #{id}
                  </Link>
                </h2>
                <Link
                  className="underline"
                  to={`/proveedores/${compra.data.proveedorId}`}
                >
                  {compra.data.proveedorNombre}
                </Link>
              </div>
              {saldo.isSuccess && tieneSaldoPendiente(saldo.data) && (
                <Button asChild>
                  <Link to={`/pagos-proveedor/compras/${id}/registrar`}>
                    Registrar pago
                  </Link>
                </Button>
              )}
            </div>
            {saldo.isSuccess && <SaldoCompra saldo={saldo.data} />}
          </section>
          <PagosAsociados id={id} />
        </>
      )}
    </>
  );
}

function PagosProveedorPage() {
  const [params] = useSearchParams();
  const id = params.get("compraId");
  const proveedorId = params.get("proveedorId") ?? "";
  const { operacion } = useOperacionPago();
  const pendiente = compraPendiente(operacion);
  return (
    <div className="grid min-w-0 gap-4">
      <PageHeader
        title="Pagos a proveedores"
        description="Elegí una compra para consultar sus pagos."
        action={
          id && (
            <Button asChild variant="outline">
              <Link to="/pagos-proveedor">Elegir otra compra</Link>
            </Button>
          )
        }
      />
      {pendiente && (
        <div>
          <Button asChild variant="outline">
            <Link to={`/pagos-proveedor/compras/${pendiente}/registrar`}>
              Revisar envío pendiente de la compra #{pendiente}
            </Link>
          </Button>
        </div>
      )}
      {id ? (
        idCatalogoValido(id) ? (
          <HistorialCompra key={id} id={id} />
        ) : (
          <RutaInvalida />
        )
      ) : (
        <SelectorCompras
          key={proveedorId}
          proveedorId={idCatalogoValido(proveedorId) ? proveedorId : undefined}
        />
      )}
    </div>
  );
}

function PagoProveedorDetalle({ id }: { id: string }) {
  const pago = usePago(id);
  return (
    <div className="grid gap-4">
      <PageHeader
        title={`Pago a proveedor #${id}`}
        action={
          <Button asChild variant="outline">
            <Link
              to={
                pago.data
                  ? `/pagos-proveedor?compraId=${pago.data.compraId}`
                  : "/pagos-proveedor"
              }
            >
              Volver a pagos
            </Link>
          </Button>
        }
      />
      <CajaFeedback query={pago} />
      {pago.isSuccess && (
        <section className="surface grid gap-5 p-4 sm:p-6">
          <PagoProveedorDatos pago={pago.data} />
          <ComprobanteOperacion
            key={id}
            operacion={{
              tipo: "PAGO_PROVEEDOR",
              id,
              padreId: pago.data.compraId,
              monto: pago.data.montoPagado,
            }}
          />
        </section>
      )}
    </div>
  );
}

function RutaInvalida() {
  return (
    <EmptyState
      title="Referencia inválida"
      description="Verificá el identificador de la compra o el pago."
      action={
        <Button asChild variant="outline">
          <Link to="/pagos-proveedor">Volver a pagos</Link>
        </Button>
      }
    />
  );
}

function PorId({ registrar = false }: { registrar?: boolean }) {
  const { id = "" } = useParams();
  if (!idCatalogoValido(id)) return <RutaInvalida />;
  return registrar ? (
    <RegistrarPagoProveedor key={id} id={id} />
  ) : (
    <PagoProveedorDetalle key={id} id={id} />
  );
}

export function PagoProveedorRoutes() {
  const { user } = useSession();
  if (!puedeGestionarPagos(user?.rol))
    return (
      <ErrorState message="No tenés permiso para acceder a pagos a proveedores." />
    );
  return (
    <Routes>
      <Route index element={<PagosProveedorPage />} />
      <Route path="compras/:id/registrar" element={<PorId registrar />} />
      <Route path=":id" element={<PorId />} />
      <Route path="*" element={<RutaInvalida />} />
    </Routes>
  );
}
