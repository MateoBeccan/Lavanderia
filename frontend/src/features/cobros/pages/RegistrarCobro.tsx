import { Link } from "react-router";
import { useCajaAbierta } from "@/features/caja/hooks/queries";
import { CajaFeedback } from "@/features/caja/components/CajaFeedback";
import { usePedido, useSaldoPedido } from "@/features/pedidos/hooks/queries";
import { PageHeader } from "@/shared/components/PageHeader";
import { ErrorState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { useMetodosCobro } from "../hooks/queries";
import { pedidoPendiente, useOperacionCobro } from "../hooks/useOperacionCobro";
import { CobroDatos } from "../components/CobroDatos";
import { FormularioCobro } from "../components/FormularioCobro";
import { RecuperarCobro } from "../components/RecuperarCobro";
import { SaldoCobro } from "../components/SaldoCobro";
import type { Cobro } from "../types/cobros";

function CobroConfirmado({
  cobro,
  cerrar,
}: {
  cobro: Cobro;
  cerrar: () => void;
}) {
  const saldo = useSaldoPedido(String(cobro.pedidoId));
  return (
    <section className="surface space-y-4 p-4 sm:p-6">
      <h2 role="status" className="text-lg font-semibold">
        Cobro #{cobro.id} registrado
      </h2>
      <CobroDatos cobro={cobro} />
      <CajaFeedback query={saldo} />
      {saldo.isSuccess && !saldo.isFetching && (
        <SaldoCobro saldo={saldo.data} />
      )}
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="outline">
          <Link to={`/cobros/${cobro.id}`}>Ver detalle del cobro</Link>
        </Button>
        <Button variant="outline" onClick={cerrar}>
          Volver al saldo del pedido
        </Button>
      </div>
    </section>
  );
}

function NuevoCobro({
  id,
  iniciar,
  mensaje,
}: {
  id: string;
  iniciar: ReturnType<typeof useOperacionCobro>["iniciar"];
  mensaje?: string;
}) {
  const pedido = usePedido(id);
  const saldo = useSaldoPedido(id);
  const caja = useCajaAbierta();
  const metodos = useMetodosCobro();
  const motivo =
    pedido.data?.estadoPedido.codigo === "CANCELADO"
      ? "El pedido está cancelado."
      : saldo.data?.saldoPendiente === 0
        ? "El pedido no tiene saldo pendiente."
        : caja.data === null
          ? "Se necesita una caja abierta para registrar un cobro."
          : metodos.data?.length === 0
            ? "No hay métodos de pago activos."
            : undefined;

  return (
    <>
      <CajaFeedback query={pedido} />
      <CajaFeedback query={saldo} />
      <CajaFeedback query={caja} />
      <CajaFeedback query={metodos} />
      {pedido.data && saldo.data && (
        <section
          className="surface space-y-3 p-4 sm:p-6"
          aria-label="Pedido seleccionado"
        >
          <p>
            <span className="font-semibold">Cliente</span>{" "}
            {pedido.data.clienteNombre} {pedido.data.clienteApellido}
          </p>
          <SaldoCobro saldo={saldo.data} />
        </section>
      )}
      {mensaje && <ErrorState message={mensaje} />}
      {motivo && <ErrorState message={motivo} />}
      {!motivo &&
        pedido.isSuccess &&
        saldo.isSuccess &&
        caja.isSuccess &&
        metodos.isSuccess && (
          <FormularioCobro
            saldo={saldo.data.saldoPendiente}
            metodos={metodos.data}
            registrar={(body) => iniciar(id, body)}
          />
        )}
    </>
  );
}

export function RegistrarCobro({ id }: { id: string }) {
  const { operacion, iniciar, reintentar, cerrar } = useOperacionCobro();
  const pendiente = pedidoPendiente(operacion);

  return (
    <div className="grid min-w-0 gap-4">
      <PageHeader
        title={`Registrar cobro · Pedido #${id}`}
        action={
          <Button asChild variant="outline">
            <Link to={`/cobros?pedidoId=${id}`}>Volver a cobros</Link>
          </Button>
        }
      />
      {pendiente && pendiente !== id ? (
        <Button asChild>
          <Link to={`/cobros/pedidos/${pendiente}/registrar`}>
            Revisar envío pendiente del pedido #{pendiente}
          </Link>
        </Button>
      ) : "intento" in operacion || operacion.estado === "VERIFICAR" ? (
        <RecuperarCobro
          id={id}
          operacion={operacion}
          reintentar={reintentar}
          cerrar={() => cerrar(id)}
        />
      ) : operacion.estado === "CONFIRMADO" &&
        String(operacion.cobro.pedidoId) === id ? (
        <CobroConfirmado cobro={operacion.cobro} cerrar={() => cerrar(id)} />
      ) : (
        <NuevoCobro
          id={id}
          iniciar={iniciar}
          mensaje={
            operacion.estado === "ERROR_VALIDADO" && operacion.pedidoId === id
              ? operacion.mensaje
              : undefined
          }
        />
      )}
    </div>
  );
}
