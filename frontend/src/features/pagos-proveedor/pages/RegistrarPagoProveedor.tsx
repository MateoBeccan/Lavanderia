import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { useSession } from "@/features/auth/session";
import { CajaFeedback } from "@/features/caja/components/CajaFeedback";
import { importeCaja } from "@/features/caja/components/formato";
import {
  saldoOptions,
  useCajaAbierta,
  useMetodosPago,
} from "@/features/caja/hooks/queries";
import { PagosAsociados } from "@/features/compras/components/PagosAsociados";
import { useCompra, useSaldoCompra } from "@/features/compras/hooks/queries";
import type { PagoAsociado } from "@/features/compras/types/compras";
import { ErrorState } from "@/shared/components/feedback/States";
import { PageHeader } from "@/shared/components/PageHeader";
import { Button } from "@/shared/components/ui/button";
import { PagoProveedorDatos } from "../components/PagoProveedorDatos";
import { PagoProveedorForm } from "../components/PagoProveedorForm";
import { RecuperarPagoProveedor } from "../components/RecuperarPagoProveedor";
import { SaldoCompra } from "../components/SaldoCompra";
import { compraPendiente, useOperacionPago } from "../hooks/useOperacionPago";
import { tieneSaldoPendiente } from "../schemas/pago";

function PagoConfirmado({
  pago,
  cerrar,
}: {
  pago: PagoAsociado;
  cerrar: () => void;
}) {
  const saldo = useSaldoCompra(String(pago.compraId));
  return (
    <section className="surface grid gap-4 p-4 sm:p-6">
      <div role="status">
        <h2 className="text-lg font-semibold">Pago #{pago.id} registrado</h2>
      </div>
      <PagoProveedorDatos pago={pago} />
      <CajaFeedback query={saldo} />
      {saldo.isSuccess && !saldo.isFetching && (
        <SaldoCompra saldo={saldo.data} />
      )}
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="outline">
          <Link to={`/pagos-proveedor/${pago.id}`}>Ver detalle del pago</Link>
        </Button>
        <Button variant="outline" onClick={cerrar}>
          Volver al saldo de la compra
        </Button>
      </div>
    </section>
  );
}

function NuevoPago({
  id,
  iniciar,
  error,
}: {
  id: string;
  iniciar: ReturnType<typeof useOperacionPago>["iniciar"];
  error?: string;
}) {
  const { api } = useSession();
  const compra = useCompra(id);
  const saldo = useSaldoCompra(id);
  const caja = useCajaAbierta();
  const metodos = useMetodosPago();
  const saldoCaja = useQuery(
    saldoOptions(api, caja.data ? String(caja.data.id) : undefined),
  );
  const motivo =
    saldo.data && !tieneSaldoPendiente(saldo.data)
      ? "La compra no tiene saldo pendiente habilitado para pagos."
      : caja.data === null || saldoCaja.data?.estadoCaja === "CERRADA"
        ? "Se necesita una caja abierta para registrar un pago."
        : metodos.data?.length === 0
          ? "No hay métodos de pago activos."
          : undefined;
  const consultas = [compra, saldo, caja, metodos];
  const verificando =
    consultas.some(({ isFetching }) => isFetching) || saldoCaja.isFetching;
  return (
    <>
      {consultas.map((query, i) => (
        <CajaFeedback key={i} query={query} />
      ))}
      {caja.data && <CajaFeedback query={saldoCaja} />}
      {compra.isSuccess && saldo.isSuccess && (
        <section
          className="surface grid gap-3 p-4 sm:p-6"
          aria-label="Compra seleccionada"
        >
          <p>
            Proveedor{" "}
            <Link
              className="font-semibold underline"
              to={`/proveedores/${compra.data.proveedorId}`}
            >
              {compra.data.proveedorNombre}
            </Link>
          </p>
          <SaldoCompra saldo={saldo.data} />
        </section>
      )}
      {saldoCaja.isSuccess && (
        <section
          className="surface space-y-2 p-4 sm:p-6"
          aria-label="Saldo de caja"
        >
          <h2 className="font-semibold">Caja #{saldoCaja.data.cajaId}</h2>
          <p>
            Saldo general{" "}
            <strong>{importeCaja(saldoCaja.data.saldoGeneral)}</strong>
          </p>
          <p>
            Efectivo disponible{" "}
            <strong>{importeCaja(saldoCaja.data.efectivoDisponible)}</strong>
          </p>
        </section>
      )}
      {error && <ErrorState message={error} />}
      {motivo && <ErrorState message={motivo} />}
      {!motivo &&
        compra.isSuccess &&
        saldo.isSuccess &&
        caja.isSuccess &&
        caja.data &&
        saldoCaja.isSuccess &&
        metodos.isSuccess && (
          <PagoProveedorForm
            key={caja.data.id}
            saldo={saldo.data.saldoPendiente}
            metodos={metodos.data}
            caja={saldoCaja.data}
            verificando={verificando}
            registrar={(body) => iniciar(id, saldoCaja.data.cajaId, body)}
          />
        )}
      {compra.isSuccess && <PagosAsociados id={id} />}
    </>
  );
}

export function RegistrarPagoProveedor({ id }: { id: string }) {
  const { operacion, iniciar, reintentar, cerrar } = useOperacionPago();
  const pendiente = compraPendiente(operacion);
  return (
    <div className="grid min-w-0 gap-4">
      <PageHeader
        title={`Registrar pago · Compra #${id}`}
        action={
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline">
              <Link to={`/compras/${id}`}>Volver a la compra</Link>
            </Button>
            <Button asChild variant="outline">
              <Link to={`/pagos-proveedor?compraId=${id}`}>Volver a pagos</Link>
            </Button>
          </div>
        }
      />
      {pendiente && pendiente !== id ? (
        <Button asChild>
          <Link to={`/pagos-proveedor/compras/${pendiente}/registrar`}>
            Revisar envío pendiente de la compra #{pendiente}
          </Link>
        </Button>
      ) : "intento" in operacion || operacion.estado === "VERIFICAR" ? (
        <RecuperarPagoProveedor
          id={id}
          operacion={operacion}
          reintentar={reintentar}
          cerrar={() => cerrar(id)}
        />
      ) : operacion.estado === "CONFIRMADA" &&
        String(operacion.pago.compraId) === id ? (
        <PagoConfirmado pago={operacion.pago} cerrar={() => cerrar(id)} />
      ) : (
        <NuevoPago
          id={id}
          iniciar={iniciar}
          error={
            operacion.estado === "ERROR" && operacion.compraId === id
              ? operacion.mensaje
              : undefined
          }
        />
      )}
    </div>
  );
}
