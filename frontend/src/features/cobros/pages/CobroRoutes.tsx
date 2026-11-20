import { useState } from "react";
import { Link, Route, Routes, useParams, useSearchParams } from "react-router";
import { useSession } from "@/features/auth/session";
import { CajaFeedback } from "@/features/caja/components/CajaFeedback";
import { ComprobanteOperacion } from "@/features/comprobantes/components/ComprobanteOperacion";
import { usePedido, usePedidos } from "@/features/pedidos/hooks/queries";
import { idPedidoValido, puedeOperarPedidos } from "@/features/pedidos/rules";
import { selectCatalogo } from "@/shared/catalogos/config";
import { PageHeader } from "@/shared/components/PageHeader";
import { EmptyState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { CobroDatos } from "../components/CobroDatos";
import { PedidoCobros } from "../components/PedidoCobros";
import { useCobro } from "../hooks/queries";
import { pedidoPendiente, useOperacionCobro } from "../hooks/useOperacionCobro";
import { RegistrarCobro } from "./RegistrarCobro";

function PedidoConsultado({ id }: { id: string }) {
  const pedido = usePedido(id);
  return (
    <>
      <CajaFeedback query={pedido} />
      {pedido.isSuccess && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <Button asChild variant="outline">
              <Link to={`/pedidos/${id}`}>Ver pedido #{id}</Link>
            </Button>
            <span>
              {pedido.data.clienteNombre} {pedido.data.clienteApellido}
            </span>
          </div>
          <PedidoCobros id={id} pedido={pedido.data} />
        </>
      )}
    </>
  );
}

function CobrosPage() {
  const [params, setParams] = useSearchParams();
  const seleccionado = params.get("pedidoId") ?? "";
  const id = idPedidoValido(seleccionado) ? seleccionado : "";
  const [page, setPage] = useState(0);
  const pedidos = usePedidos({ page, size: 20, sort: "id,desc" });
  const opciones = pedidos.data?.content ?? [];
  const { operacion } = useOperacionCobro();
  const pendiente = pedidoPendiente(operacion);

  return (
    <div className="grid min-w-0 gap-4">
      <PageHeader
        title="Cobros"
        description="Consultá los cobros registrados por pedido."
      />
      {pendiente && (
        <Button asChild className="justify-self-start">
          <Link to={`/cobros/pedidos/${pendiente}/registrar`}>
            Revisar envío pendiente
          </Link>
        </Button>
      )}
      <section
        className="surface grid gap-3 p-4 sm:p-6"
        aria-label="Selección de pedido"
      >
        <FormField id="cobros-pedido" label="Pedido">
          <select
            id="cobros-pedido"
            className={selectCatalogo}
            value={id}
            onChange={(event) =>
              setParams(
                event.target.value ? { pedidoId: event.target.value } : {},
              )
            }
          >
            <option value="">Seleccioná un pedido</option>
            {id && !opciones.some((opcion) => String(opcion.id) === id) && (
              <option value={id}>Pedido #{id}</option>
            )}
            {opciones.map((opcion) => (
              <option key={opcion.id} value={opcion.id}>
                #{opcion.id} · {opcion.clienteNombre} {opcion.clienteApellido}
              </option>
            ))}
          </select>
        </FormField>
        <CajaFeedback query={pedidos} />
        {pedidos.isSuccess && pedidos.data.page.totalPages > 1 && (
          <nav
            className="flex flex-wrap items-center gap-2"
            aria-label="Páginas de pedidos"
          >
            <Button
              variant="outline"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Pedidos anteriores
            </Button>
            <span aria-live="polite">
              Página {page + 1} de {pedidos.data.page.totalPages}
            </span>
            <Button
              variant="outline"
              disabled={page + 1 >= pedidos.data.page.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Más pedidos
            </Button>
          </nav>
        )}
      </section>
      {id ? (
        <PedidoConsultado key={id} id={id} />
      ) : (
        <EmptyState
          title={
            pedidos.isSuccess && !opciones.length
              ? "Sin pedidos"
              : "Elegí un pedido"
          }
          description="El historial de cobros se consulta por pedido."
        />
      )}
    </div>
  );
}

function CobroDetalle({ id }: { id: string }) {
  const cobro = useCobro(id);
  return (
    <div className="grid min-w-0 gap-4">
      <PageHeader
        title={`Cobro #${id}`}
        action={
          <Button asChild variant="outline">
            <Link
              to={
                cobro.data
                  ? `/cobros?pedidoId=${cobro.data.pedidoId}`
                  : "/cobros"
              }
            >
              Volver a cobros
            </Link>
          </Button>
        }
      />
      <CajaFeedback query={cobro} />
      {cobro.isSuccess && (
        <section className="surface grid gap-5 p-4 sm:p-6">
          <CobroDatos cobro={cobro.data} />
          <ComprobanteOperacion
            key={id}
            operacion={{
              tipo: "COBRO",
              id,
              padreId: cobro.data.pedidoId,
              monto: cobro.data.montoCobrado,
            }}
          />
        </section>
      )}
    </div>
  );
}

function CobroPorId({ registrar = false }: { registrar?: boolean }) {
  const { id = "" } = useParams();
  if (!idPedidoValido(id)) return <CobroNoEncontrado />;
  return registrar ? (
    <RegistrarCobro key={id} id={id} />
  ) : (
    <CobroDetalle id={id} />
  );
}

function CobroNoEncontrado() {
  return (
    <EmptyState
      title="Página no encontrada"
      description="Revisá el número del pedido o del cobro."
      action={
        <Button asChild>
          <Link to="/cobros">Ir a cobros</Link>
        </Button>
      }
    />
  );
}

export function CobroRoutes() {
  const { user } = useSession();
  if (!user || !puedeOperarPedidos(user.rol)) {
    return (
      <EmptyState
        title="Acceso restringido"
        description="Tu rol no tiene permiso para operar cobros."
      />
    );
  }
  return (
    <Routes>
      <Route index element={<CobrosPage />} />
      <Route path=":id" element={<CobroPorId />} />
      <Route path="pedidos/:id/registrar" element={<CobroPorId registrar />} />
      <Route path="*" element={<CobroNoEncontrado />} />
    </Routes>
  );
}
