import { Link, Route, Routes, useParams } from "react-router";
import { useSession } from "@/features/auth/session";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { PageHeader } from "@/shared/components/PageHeader";
import { Button } from "@/shared/components/ui/button";
import { CompraForm } from "../components/CompraForm";
import {
  compraEditable,
  puedeGestionarCompras,
} from "../components/presentacion";
import { useCompra, useSaldoCompra } from "../hooks/queries";
import { CompraDetallePage } from "./CompraDetallePage";
import { ComprasPage } from "./ComprasPage";

function VolverCompras({ id }: { id?: string }) {
  return (
    <Button asChild variant="outline">
      <Link to={id ? `/compras/${id}` : "/compras"}>
        {id ? "Volver a la compra" : "Volver a compras"}
      </Link>
    </Button>
  );
}

function EditarCompra({ id }: { id: string }) {
  const consulta = useCompra(id);
  const saldo = useSaldoCompra(id);
  const compra = consulta.data;
  return (
    <div className="space-y-5">
      <PageHeader
        title={`Editar compra #${id}`}
        action={<VolverCompras id={id} />}
      />
      {(consulta.isPending || saldo.isPending) && (
        <LoadingState label="Verificando la compra…" />
      )}
      {consulta.isError && (
        <ErrorState
          message={consulta.error.message}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {saldo.isError && (
        <ErrorState
          message={saldo.error.message}
          onRetry={() => void saldo.refetch()}
        />
      )}
      {compra &&
        saldo.data &&
        !consulta.isError &&
        !saldo.isError &&
        (compraEditable(compra, saldo.data) ? (
          <CompraForm key={id} compra={compra} />
        ) : (
          <EmptyState
            title="La compra no se puede editar"
            description="Solo se pueden modificar compras pendientes y sin pagos confirmados."
          />
        ))}
    </div>
  );
}

function CompraPorId({ editar = false }: { editar?: boolean }) {
  const { id } = useParams();
  if (!id || !/^[1-9]\d{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n)
    return <RutaInvalida />;
  return editar ? (
    <EditarCompra key={id} id={id} />
  ) : (
    <CompraDetallePage key={id} id={id} />
  );
}

function RutaInvalida() {
  return (
    <EmptyState
      title="Compra no encontrada"
      description="Verificá el identificador o buscá la compra en el listado."
      action={<VolverCompras />}
    />
  );
}

export function CompraRoutes() {
  const { user } = useSession();
  if (!puedeGestionarCompras(user?.rol))
    return <ErrorState message="No tenés permiso para acceder a compras." />;
  return (
    <Routes>
      <Route index element={<ComprasPage />} />
      <Route
        path="nueva"
        element={
          <div className="space-y-5">
            <PageHeader title="Nueva compra" action={<VolverCompras />} />
            <CompraForm />
          </div>
        }
      />
      <Route path=":id" element={<CompraPorId />} />
      <Route path=":id/editar" element={<CompraPorId editar />} />
      <Route path="*" element={<RutaInvalida />} />
    </Routes>
  );
}
