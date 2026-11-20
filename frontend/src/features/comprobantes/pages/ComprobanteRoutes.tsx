import { useQuery } from "@tanstack/react-query";
import { Printer } from "lucide-react";
import { Link, Route, Routes, useParams } from "react-router";
import { useSession } from "@/features/auth/session";
import { cajaApi } from "@/features/caja/api/caja";
import { importeCaja } from "@/features/caja/components/formato";
import { cajaKeys } from "@/features/caja/hooks/queries";
import {
  fechaPedido,
  idPedidoValido,
  puedeOperarPedidos,
} from "@/features/pedidos/rules";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { PageHeader } from "@/shared/components/PageHeader";
import { Button } from "@/shared/components/ui/button";
import { nombreTipo } from "../components/presentacion";
import { useComprobante } from "../hooks/queries";

function AccesoComprobantes() {
  return (
    <div className="grid gap-4">
      <PageHeader
        title="Comprobantes"
        description="Consultá los comprobantes internos desde el pedido o la compra vinculada."
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <section className="surface grid gap-3 p-5">
          <h2 className="text-lg font-semibold">Comprobantes de cobro</h2>
          <Button asChild variant="outline" className="justify-self-start">
            <Link to="/pedidos">Buscar pedido</Link>
          </Button>
        </section>
        <section className="surface grid gap-3 p-5">
          <h2 className="text-lg font-semibold">
            Comprobantes de pago a proveedores
          </h2>
          <Button asChild variant="outline" className="justify-self-start">
            <Link to="/compras">Buscar compra</Link>
          </Button>
        </section>
      </div>
    </div>
  );
}

function ReferenciaMovimiento({ id }: { id: number }) {
  const { api } = useSession();
  const movimiento = useQuery({
    queryKey: cajaKeys.movimiento(String(id)),
    queryFn: ({ signal }) => cajaApi.movimiento(api, String(id), signal),
  });
  const datos = movimiento.data;
  return (
    <>
      <span className={datos ? "hidden print:inline" : undefined}>
        Movimiento #{id}
      </span>
      {datos && (
        <Link
          className="underline print:hidden"
          to={`/caja/${datos.cajaId}/movimientos/${id}`}
        >
          Movimiento #{id}
        </Link>
      )}
      {movimiento.isError && (
        <div className="mt-2 print:hidden">
          <ErrorState
            message="No pudimos abrir el enlace al movimiento de Caja."
            onRetry={() => void movimiento.refetch()}
          />
        </div>
      )}
    </>
  );
}

function ComprobanteDetalle({ id }: { id: string }) {
  const consulta = useComprobante(id);
  const datos = consulta.isSuccess ? consulta.data : undefined;
  const padre =
    datos?.tipoOperacion === "COBRO"
      ? {
          ruta: `/pedidos/${datos.pedidoId}`,
          nombre: `Pedido #${datos.pedidoId}`,
        }
      : datos && {
          ruta: `/compras/${datos.compraId}`,
          nombre: `Compra #${datos.compraId}`,
        };
  const operacion =
    datos?.tipoOperacion === "COBRO"
      ? { ruta: `/cobros/${datos.cobroId}`, nombre: `Cobro #${datos.cobroId}` }
      : datos && {
          ruta: `/pagos-proveedor/${datos.pagoProveedorId}`,
          nombre: `Pago a proveedor #${datos.pagoProveedorId}`,
        };
  return (
    <div className="grid min-w-0 gap-4">
      <div className="print:hidden">
        <PageHeader
          title={`Comprobante #${id}`}
          action={
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="outline">
                <Link to="/comprobantes">Ir a comprobantes</Link>
              </Button>
              {datos && (
                <Button onClick={() => window.print()}>
                  <Printer size={16} aria-hidden="true" /> Imprimir constancia
                </Button>
              )}
            </div>
          }
        />
      </div>
      {consulta.isPending && <LoadingState label="Cargando comprobante…" />}
      {consulta.isError && (
        <ErrorState
          message={consulta.error.message}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {datos && padre && operacion && (
        <article
          aria-label="Constancia interna"
          className="comprobante-imprimible surface grid gap-5 p-5 sm:p-7"
        >
          <div className="hidden print:block">
            <p className="font-semibold">Mirá que te lavo</p>
            <h1 className="text-xl font-bold">Comprobante #{datos.id}</h1>
          </div>
          <p className="text-sm font-semibold">
            Constancia interna sin validez fiscal
          </p>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Tipo de operación</dt>
              <dd>{nombreTipo(datos.tipoOperacion)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Fecha de emisión</dt>
              <dd>
                <time dateTime={datos.fechaEmision}>
                  {fechaPedido(datos.fechaEmision)}
                </time>
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Importe de la operación</dt>
              <dd className="text-xl font-bold tabular-nums">
                {importeCaja(datos.montoOperacion)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Método de pago</dt>
              <dd>
                {datos.metodoPagoNombre} ({datos.metodoPagoCodigo})
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Operación</dt>
              <dd>
                <Link
                  className="underline print:no-underline"
                  to={operacion.ruta}
                >
                  {operacion.nombre}
                </Link>
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Vinculado a</dt>
              <dd>
                <Link className="underline print:no-underline" to={padre.ruta}>
                  {padre.nombre}
                </Link>
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Caja</dt>
              <dd>
                <ReferenciaMovimiento id={datos.movimientoCajaId} />
              </dd>
            </div>
            {datos.observaciones && (
              <div className="sm:col-span-2">
                <dt className="text-muted-foreground">Observaciones</dt>
                <dd className="whitespace-pre-wrap wrap-anywhere">
                  {datos.observaciones}
                </dd>
              </div>
            )}
          </dl>
        </article>
      )}
    </div>
  );
}

function RutaInvalida() {
  return (
    <EmptyState
      title="Comprobante no encontrado"
      description="Verificá el identificador del comprobante."
      action={
        <Button asChild variant="outline">
          <Link to="/comprobantes">Ir a comprobantes</Link>
        </Button>
      }
    />
  );
}

function PorId() {
  const { id = "" } = useParams();
  return idPedidoValido(id) ? <ComprobanteDetalle id={id} /> : <RutaInvalida />;
}

export function ComprobanteRoutes() {
  const { user } = useSession();
  if (!user || !puedeOperarPedidos(user.rol)) {
    return (
      <ErrorState message="No tenés permiso para acceder a comprobantes." />
    );
  }
  return (
    <Routes>
      <Route index element={<AccesoComprobantes />} />
      <Route path=":id" element={<PorId />} />
      <Route path="*" element={<RutaInvalida />} />
    </Routes>
  );
}
