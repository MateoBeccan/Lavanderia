import type { SaldoPedido } from "@/features/pedidos/types/pedidos";
import { importeCaja } from "@/features/caja/components/formato";

export function SaldoCobro({ saldo }: { saldo: SaldoPedido }) {
  return (
    <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <div>
        <dt className="text-sm text-muted-foreground">Total del pedido</dt>
        <dd>{importeCaja(saldo.totalPedido)}</dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Total cobrado</dt>
        <dd>{importeCaja(saldo.totalCobrado)}</dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Saldo pendiente</dt>
        <dd className="font-semibold">{importeCaja(saldo.saldoPendiente)}</dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">Estado de pago</dt>
        <dd>{saldo.estadoPagoNombre}</dd>
      </div>
    </dl>
  );
}
