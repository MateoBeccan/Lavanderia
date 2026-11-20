import { Link } from "react-router";
import { fechaCaja, importeCaja } from "@/features/caja/components/formato";
import type { Cobro } from "../types/cobros";

export function CobroDatos({ cobro }: { cobro: Cobro }) {
  return (
    <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
      <div>
        <dt className="text-sm text-muted-foreground">Importe recibido</dt>
        <dd className="font-semibold">{importeCaja(cobro.montoCobrado)}</dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Método de pago</dt>
        <dd>{cobro.metodoPagoNombre}</dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Registrado por</dt>
        <dd>{cobro.usuarioNombre}</dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Fecha</dt>
        <dd>
          <time dateTime={cobro.fechaCobro}>{fechaCaja(cobro.fechaCobro)}</time>
        </dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Pedido relacionado</dt>
        <dd>
          <Link className="underline" to={`/pedidos/${cobro.pedidoId}`}>
            Pedido #{cobro.pedidoId}
          </Link>
        </dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Caja #{cobro.cajaId}</dt>
        <dd>
          <Link
            className="underline"
            to={`/caja/${cobro.cajaId}/movimientos/${cobro.movimientoCajaId}`}
          >
            Movimiento #{cobro.movimientoCajaId}
          </Link>
        </dd>
      </div>
    </dl>
  );
}
