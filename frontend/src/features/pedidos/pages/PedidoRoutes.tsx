import { Link, Route, Routes, useLocation, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { PedidoCobros } from "@/features/cobros/components/PedidoCobros";
import { ComprobantesAsociados } from "@/features/comprobantes/components/ComprobantesAsociados";
import { ApiError } from "@/shared/api/http";
import { PageHeader } from "@/shared/components/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { PedidoEstadoBadge } from "../components/PedidoEstadoBadge";
import { PedidoEstadoDialog } from "../components/PedidoEstadoDialog";
import { PedidoForm } from "../components/PedidoForm";
import { PedidoHistorial } from "../components/PedidoHistorial";
import { useEstadosPedido, usePedido, useTieneCobros } from "../hooks/queries";
import {
  destinosPedido,
  errorPedido,
  estadoEditable,
  fechaPedido,
  idPedidoValido,
  importePedido,
  puedeOperarPedidos,
} from "../rules";
import type { Pedido } from "../types/pedidos";
import { PedidosPage } from "./PedidosPage";

function VolverPedidos() {
  const { search } = useLocation();
  return (
    <Button asChild variant="outline">
      <Link to={{ pathname: "/pedidos", search }}>Volver a pedidos</Link>
    </Button>
  );
}

function PedidoNoEncontrado() {
  return (
    <EmptyState
      title="Pedido no encontrado"
      description="Revisá el número del pedido."
      action={<VolverPedidos />}
    />
  );
}

function PedidoFicha({
  pedido,
  id,
  cobros,
}: {
  pedido: Pedido;
  id: string;
  cobros: ReturnType<typeof useTieneCobros>;
}) {
  const { search } = useLocation();
  const estados = useEstadosPedido();
  const cache = useQueryClient();
  const actual = pedido.estadoPedido;
  const destinos = destinosPedido(
    actual.codigo,
    estados.isSuccess ? estados.data : [],
    cobros.isSuccess ? cobros.data : undefined,
  );
  const editar =
    estadoEditable(actual.codigo) && cobros.isSuccess && !cobros.data;
  return (
    <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-5">
      <PageHeader
        title={`Pedido #${pedido.id}`}
        action={
          <div className="flex flex-wrap gap-2">
            <VolverPedidos />
            {editar && (
              <Button asChild variant="outline">
                <Link to={{ pathname: `/pedidos/${id}/editar`, search }}>
                  Editar pedido
                </Link>
              </Button>
            )}
            <PedidoEstadoDialog
              pedido={pedido}
              destinos={destinos}
              verificar={() =>
                cache.invalidateQueries({ queryKey: ["pedidos"] })
              }
            />
          </div>
        }
      />
      {estados.isError && (
        <ErrorState
          message={errorPedido(estados.error)}
          onRetry={() => void estados.refetch()}
        />
      )}
      {cobros.isError && (
        <ErrorState
          message={errorPedido(cobros.error)}
          onRetry={() => void cobros.refetch()}
        />
      )}
      <section className="surface p-4 sm:p-6" aria-labelledby="datos-pedido">
        <h2 className="mb-4 text-lg font-semibold" id="datos-pedido">
          Datos del pedido
        </h2>
        <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <dt className="text-sm text-muted-foreground">Cliente</dt>
            <dd>
              {pedido.clienteNombre} {pedido.clienteApellido} · ID{" "}
              {pedido.clienteId}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">Registrado por</dt>
            <dd>{pedido.usuarioNombre}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">Estado actual</dt>
            <dd>
              <PedidoEstadoBadge {...actual} />
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">Ingreso</dt>
            <dd>
              <time dateTime={pedido.fechaIngreso}>
                {fechaPedido(pedido.fechaIngreso, true)}
              </time>
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">Entrega estimada</dt>
            <dd>
              <time dateTime={pedido.fechaEntregaEstimada}>
                {fechaPedido(pedido.fechaEntregaEstimada)}
              </time>
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">Total confirmado</dt>
            <dd className="text-lg font-semibold">
              {importePedido(pedido.total)}
            </dd>
          </div>
        </dl>
        {cobros.data === true && (
          <p className="mt-4 text-sm text-muted-foreground">
            El pedido tiene cobros registrados. La edición y la cancelación
            están deshabilitadas.
          </p>
        )}
      </section>
      <DataTable
        caption="Renglones del pedido"
        data={pedido.detalles}
        getRowId={(d) => String(d.id)}
        columns={[
          { header: "Prenda", accessorKey: "prendaNombre" },
          { header: "Servicio", accessorKey: "servicioNombre" },
          {
            header: "Cantidad",
            id: "cantidad",
            cell: ({ row }) =>
              row.original.cantidad.toLocaleString("es-AR") +
              (row.original.modalidadCobro === "KILO" ? " kg" : ""),
          },
          {
            header: "Precio unitario",
            id: "precio",
            cell: ({ row }) => importePedido(row.original.precioUnitario),
          },
          {
            header: "Subtotal",
            id: "subtotal",
            cell: ({ row }) => importePedido(row.original.subtotal),
          },
          {
            header: "Estado de la prenda",
            id: "estadoPrenda",
            cell: ({ row }) => row.original.estadoPrenda || "—",
          },
          {
            header: "Observaciones",
            id: "observaciones",
            cell: ({ row }) => (
              <span className="block min-w-32 max-w-72 whitespace-normal wrap-anywhere">
                {row.original.observaciones || "—"}
              </span>
            ),
          },
        ]}
      />
      <PedidoCobros pedido={pedido} id={id} />
      <ComprobantesAsociados key={id} padre="pedidos" id={id} />
      <PedidoHistorial id={id} />
    </div>
  );
}

function PedidoExistente({ id, editar }: { id: string; editar: boolean }) {
  const { search } = useLocation();
  const consulta = usePedido(id);
  const codigo = consulta.data?.estadoPedido.codigo;
  const cobros = useTieneCobros(id, !!codigo && estadoEditable(codigo));
  if (consulta.isPending) return <LoadingState label="Cargando pedido…" />;
  if (consulta.isError) {
    if (consulta.error instanceof ApiError && consulta.error.status === 404)
      return <PedidoNoEncontrado />;
    if (!consulta.data || !editar)
      return (
        <>
          <VolverPedidos />
          <ErrorState
            message={errorPedido(consulta.error)}
            onRetry={() => void consulta.refetch()}
          />
        </>
      );
  }
  const pedido = consulta.data;
  if (!pedido) return <PedidoNoEncontrado />;
  if (!editar) return <PedidoFicha pedido={pedido} id={id} cobros={cobros} />;

  const volver = (
    <Button asChild variant="outline">
      <Link to={{ pathname: `/pedidos/${id}`, search }}>Volver al pedido</Link>
    </Button>
  );
  if (!estadoEditable(pedido.estadoPedido.codigo) || cobros.data === true) {
    return (
      <EmptyState
        title="Edición no disponible"
        description={
          cobros.data
            ? "El pedido tiene cobros registrados."
            : "El estado actual impide editar este pedido."
        }
        action={volver}
      />
    );
  }
  if (cobros.isPending)
    return <LoadingState label="Verificando disponibilidad de edición…" />;
  if (cobros.isError && cobros.data === undefined)
    return (
      <>
        {volver}
        <ErrorState
          message={errorPedido(cobros.error)}
          onRetry={() => void cobros.refetch()}
        />
      </>
    );

  return (
    <>
      <PageHeader title={`Editar pedido #${id}`} />
      <PedidoForm
        pedido={pedido}
        autorizado={
          consulta.isSuccess && cobros.isSuccess && cobros.data === false
        }
      />
    </>
  );
}

function PedidoPorId({ editar = false }: { editar?: boolean }) {
  const { id = "" } = useParams();
  return idPedidoValido(id) ? (
    <PedidoExistente key={id} id={id} editar={editar} />
  ) : (
    <PedidoNoEncontrado />
  );
}
function NuevoPedido() {
  return (
    <>
      <PageHeader title="Nuevo pedido" />
      <PedidoForm />
    </>
  );
}

export function PedidoRoutes() {
  const { user } = useSession();
  if (!user || !puedeOperarPedidos(user.rol)) {
    return (
      <EmptyState
        title="Acceso restringido"
        description="Tu rol no tiene permiso para operar pedidos."
      />
    );
  }

  return (
    <Routes>
      <Route index element={<PedidosPage />} />
      <Route path="nuevo" element={<NuevoPedido />} />
      <Route path=":id" element={<PedidoPorId />} />
      <Route path=":id/editar" element={<PedidoPorId editar />} />
      <Route path="*" element={<PedidoNoEncontrado />} />
    </Routes>
  );
}
