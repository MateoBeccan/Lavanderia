import { Link } from "react-router";
import { fechaCaja, importeCaja } from "@/features/caja/components/formato";
import type { PagoAsociado } from "@/features/compras/types/compras";

export function PagoProveedorDatos({ pago }: { pago: PagoAsociado }) {
  return (
    <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div>
        <dt>Compra</dt>
        <dd>
          <Link className="underline" to={`/compras/${pago.compraId}`}>
            Compra #{pago.compraId}
          </Link>
        </dd>
      </div>
      <div>
        <dt>Proveedor</dt>
        <dd>
          <Link className="underline" to={`/proveedores/${pago.proveedorId}`}>
            {pago.proveedorNombre}
          </Link>
        </dd>
      </div>
      <div>
        <dt>Importe</dt>
        <dd className="font-semibold tabular-nums">
          {importeCaja(pago.montoPagado)}
        </dd>
      </div>
      <div>
        <dt>Método de pago</dt>
        <dd>{pago.metodoPagoNombre}</dd>
      </div>
      <div>
        <dt>Fecha de registro</dt>
        <dd>{fechaCaja(pago.fechaPago)}</dd>
      </div>
      <div>
        <dt>Registrado por</dt>
        <dd>{pago.usuarioNombre}</dd>
      </div>
      <div>
        <dt>Movimiento de caja</dt>
        <dd>
          <Link
            className="underline"
            to={`/caja/${pago.cajaId}/movimientos/${pago.movimientoCajaId}`}
          >
            Movimiento #{pago.movimientoCajaId}
          </Link>
        </dd>
      </div>
    </dl>
  );
}
