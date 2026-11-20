import { Link } from "react-router";
import { useCajaAbierta } from "@/features/caja/hooks/queries";
import { CajaFeedback } from "@/features/caja/components/CajaFeedback";
import { useSaldoPedido } from "@/features/pedidos/hooks/queries";
import type { Pedido } from "@/features/pedidos/types/pedidos";
import { Button } from "@/shared/components/ui/button";
import { pedidoPendiente, useOperacionCobro } from "../hooks/useOperacionCobro";
import { HistorialCobros } from "./HistorialCobros";
import { SaldoCobro } from "./SaldoCobro";

export function PedidoCobros({ pedido, id }: { pedido: Pedido; id: string }) {
  const saldo = useSaldoPedido(id);
  const caja = useCajaAbierta();
  const { operacion } = useOperacionCobro();
  const pendiente = pedidoPendiente(operacion);
  const habilitado =
    !pendiente &&
    pedido.estadoPedido.codigo !== "CANCELADO" &&
    saldo.isSuccess &&
    saldo.data.saldoPendiente > 0 &&
    caja.isSuccess &&
    !!caja.data;
  return (
    <>
      <section
        className="surface space-y-4 p-4 sm:p-6"
        aria-labelledby="pago-pedido"
      >
        <h2 className="text-lg font-semibold" id="pago-pedido">
          Información de pago
        </h2>
        <CajaFeedback query={saldo} />
        {saldo.isSuccess && <SaldoCobro saldo={saldo.data} />}
        {habilitado && (
          <Button asChild>
            <Link to={`/cobros/pedidos/${id}/registrar`}>Registrar cobro</Link>
          </Button>
        )}
        {pendiente && (
          <Button variant="outline" asChild>
            <Link to={`/cobros/pedidos/${pendiente}/registrar`}>
              Revisar envío pendiente
            </Link>
          </Button>
        )}
        {!pendiente &&
          saldo.isSuccess &&
          saldo.data.saldoPendiente > 0 &&
          pedido.estadoPedido.codigo !== "CANCELADO" && (
            <>
              <CajaFeedback query={caja} />
              {caja.isSuccess && caja.data === null && (
                <p className="text-sm text-muted-foreground">
                  Se necesita una caja abierta para registrar un cobro.
                </p>
              )}
            </>
          )}
      </section>
      <HistorialCobros key={id} id={id} />
    </>
  );
}
