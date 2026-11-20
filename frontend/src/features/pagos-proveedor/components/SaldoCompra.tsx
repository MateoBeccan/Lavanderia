import { importeCaja } from "@/features/caja/components/formato";
import type { SaldoCompra as Saldo } from "@/features/compras/types/compras";

export function SaldoCompra({ saldo }: { saldo: Saldo }) {
  return (
    <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div>
        <dt>Total de compra</dt>
        <dd>{importeCaja(saldo.totalCompra)}</dd>
      </div>
      <div>
        <dt>Total pagado</dt>
        <dd>{importeCaja(saldo.totalPagado)}</dd>
      </div>
      <div>
        <dt>Saldo pendiente</dt>
        <dd className="text-xl font-bold tabular-nums">
          {importeCaja(saldo.saldoPendiente)}
        </dd>
      </div>
      <div>
        <dt>Estado de pago</dt>
        <dd>{saldo.estadoPagoNombre}</dd>
      </div>
    </dl>
  );
}
