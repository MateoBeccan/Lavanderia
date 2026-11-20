import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, Route, Routes, useParams } from "react-router";
import { Wallet, Banknote } from "lucide-react";
import { useSession } from "@/features/auth/session";
import { PageHeader } from "@/shared/components/PageHeader";
import { StatCard } from "@/shared/components/StatCard";
import { DataTable } from "@/shared/components/tables/DataTable";
import { EmptyState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { cajaApi } from "../api/caja";
import { cajaKeys, saldoOptions, useCajaAbierta } from "../hooks/queries";
import type { CajaResumen } from "../types/caja";
import { CierreCaja } from "../components/CierreCaja";
import { AccionesCaja, AperturaCaja } from "../components/AccionesCaja";
import { ResultadoCaja } from "../components/ResultadoCaja";
import { useOperacionCaja } from "../hooks/useOperacionCaja";
import type { SolicitudCaja } from "../api/escrituras";
import { CajaFeedback } from "../components/CajaFeedback";
import { MovimientosCaja, Sentido } from "../components/MovimientosCaja";
import { fechaCaja, fechaConsulta, importeCaja } from "../components/formato";

const idValido = (id?: string) =>
  id && /^[1-9][0-9]{0,18}$/.test(id) && BigInt(id) <= 9223372036854775807n;
const estado = (codigo: string) =>
  codigo === "ABIERTA" ? "Abierta" : "Cerrada";
const historialLink = (
  <Button asChild variant="outline">
    <Link to="/caja/historial">Historial de cajas</Link>
  </Button>
);
const volver = (
  <Button asChild variant="outline">
    <Link to="/caja">Volver a caja actual</Link>
  </Button>
);

function CajaActual() {
  const { user } = useSession();
  const abierta = useCajaAbierta();
  const flujo = useOperacionCaja();
  const acciones =
    flujo.permitido && ["LISTA", "ERROR"].includes(flujo.operacion.estado)
      ? flujo.iniciar
      : undefined;
  return (
    <>
      <PageHeader
        title="Caja"
        description="Saldo oficial, arqueo e historial de movimientos."
      />
      {user?.rol === "ADMINISTRADOR" && historialLink}
      <ResultadoCaja flujo={flujo} cajaActual={abierta.data?.id} />
      <CajaFeedback query={abierta} />
      {!abierta.error && abierta.data === null && (
        <EmptyState
          title="Sin caja abierta"
          description="Abrí una caja para iniciar la jornada."
        />
      )}
      {acciones && abierta.isSuccess && abierta.data === null && (
        <AperturaCaja iniciar={acciones} deshabilitado={abierta.isFetching} />
      )}
      {!abierta.error && abierta.data && (
        <PanelCaja
          key={abierta.data.id}
          id={String(abierta.data.id)}
          iniciar={acciones}
          actualizando={abierta.isFetching}
        />
      )}
    </>
  );
}

function PanelCaja({
  id,
  iniciar,
  actualizando,
}: {
  id: string;
  iniciar?: (solicitud: SolicitudCaja) => void;
  actualizando?: boolean;
}) {
  const { api, user } = useSession();
  const saldo = useQuery(saldoOptions(api, id));
  const data = saldo.data;
  return (
    <div className="space-y-5">
      <CajaFeedback query={saldo} />
      {!saldo.error && data && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-xl font-semibold">
              Caja #{id} · {estado(data.estadoCaja)}
            </h2>
            <span className="text-sm text-muted-foreground">
              Consultado el {fechaConsulta(saldo.dataUpdatedAt)}
            </span>
          </div>
          <div className="metrics-grid">
            <StatCard
              title="Saldo general"
              icon={Wallet}
              footnote={"Monto inicial: " + importeCaja(data.montoInicial)}
            >
              {importeCaja(data.saldoGeneral)}
            </StatCard>
            <StatCard
              title={
                data.estadoCaja === "ABIERTA"
                  ? "Efectivo disponible"
                  : "Efectivo esperado al cierre"
              }
              icon={Banknote}
              footnote={
                data.efectivoContado === null
                  ? "Movimientos en efectivo"
                  : "Efectivo contado: " + importeCaja(data.efectivoContado)
              }
            >
              {importeCaja(data.efectivoDisponible)}
            </StatCard>
          </div>
          <div className="surface space-y-2 p-5 text-sm">
            <p>
              Apertura por {data.usuarioAperturaNombre} ·{" "}
              {fechaCaja(data.fechaApertura)}
            </p>
            {data.fechaCierre && (
              <p>
                Cierre por {data.usuarioCierreNombre} ·{" "}
                {fechaCaja(data.fechaCierre)}
              </p>
            )}
            {data.diferenciaArqueo !== null && (
              <p>Diferencia de arqueo {importeCaja(data.diferenciaArqueo)}</p>
            )}
          </div>
          {data.estadoCaja === "ABIERTA" && (
            <>
              {iniciar && (
                <AccionesCaja
                  saldo={data}
                  iniciar={iniciar}
                  deshabilitado={!!actualizando || saldo.isFetching}
                />
              )}
              {user?.rol === "ADMINISTRADOR" && <CierreCaja id={id} />}
            </>
          )}
        </>
      )}
      <MovimientosCaja id={id} />
    </div>
  );
}

function HistorialCajas() {
  const { api, user } = useSession();
  const [page, setPage] = useState(0);
  const admin = user?.rol === "ADMINISTRADOR";
  const historial = useQuery({
    queryKey: cajaKeys.historial(page),
    enabled: admin,
    queryFn: ({ signal }) => cajaApi.historial(api, page, signal),
  });
  if (!admin)
    return (
      <EmptyState
        title="Acceso restringido"
        description="El historial de cajas requiere el rol de administrador."
        action={volver}
      />
    );
  return (
    <>
      <PageHeader
        title="Historial de cajas"
        description="Cajas por fecha de apertura, más recientes primero."
      />
      {volver}
      <CajaFeedback query={historial} />
      {!historial.error &&
        historial.data &&
        (historial.data.page.totalElements === 0 ? (
          <EmptyState
            title="Sin cajas registradas"
            description="Las cajas aparecerán aquí cuando se registren en el servidor."
          />
        ) : (
          <DataTable<CajaResumen>
            caption="Historial de cajas"
            data={historial.data.content}
            getRowId={(row) => String(row.id)}
            pagination={{
              pageIndex: page,
              totalPages: historial.data.page.totalPages,
              onPageChange: setPage,
            }}
            columns={[
              {
                id: "id",
                header: "Caja",
                cell: ({ row }) => (
                  <Link className="underline" to={`/caja/${row.original.id}`}>
                    Caja #{row.original.id}
                  </Link>
                ),
              },
              {
                id: "estado",
                header: "Estado",
                cell: ({ row }) => estado(row.original.estado),
              },
              {
                id: "apertura",
                header: "Apertura",
                cell: ({ row }) => fechaCaja(row.original.fechaApertura),
              },
              {
                id: "cierre",
                header: "Cierre",
                cell: ({ row }) =>
                  row.original.fechaCierre
                    ? fechaCaja(row.original.fechaCierre)
                    : "—",
              },
            ]}
          />
        ))}
    </>
  );
}

function CajaPorId() {
  const { id } = useParams();
  if (!id || !idValido(id)) return <RutaInvalida />;
  return (
    <>
      <PageHeader title={"Caja #" + id} />
      {volver}
      <PanelCaja key={id} id={id} />
    </>
  );
}

function DetalleMovimiento() {
  const { id, movimientoId } = useParams();
  const { api } = useSession();
  const validos = !!idValido(id) && !!idValido(movimientoId);
  const movimiento = useQuery({
    queryKey: cajaKeys.movimiento(movimientoId ?? ""),
    enabled: validos,
    queryFn: ({ signal }) => cajaApi.movimiento(api, movimientoId!, signal),
  });
  if (!validos) return <RutaInvalida />;
  const data = movimiento.data;
  return (
    <>
      <PageHeader title={"Movimiento #" + movimientoId} />
      <Button asChild variant="outline">
        <Link to={`/caja/${id}`}>Volver a caja #{id}</Link>
      </Button>
      <CajaFeedback query={movimiento} />
      {!movimiento.error &&
        data &&
        (String(data.cajaId) !== id ? (
          <EmptyState
            title="Movimiento fuera de la caja"
            description="El movimiento solicitado pertenece a otra caja."
          />
        ) : (
          <dl className="surface grid gap-4 break-words p-5 sm:grid-cols-2">
            <div>
              <dt className="font-semibold">Tipo</dt>
              <dd>
                <Sentido codigo={data.tipoMovimientoCodigo} />{" "}
                {data.tipoMovimientoNombre}
              </dd>
            </div>
            <div>
              <dt className="font-semibold">Importe</dt>
              <dd>{importeCaja(data.monto)}</dd>
            </div>
            <div>
              <dt className="font-semibold">Fecha y hora</dt>
              <dd>{fechaCaja(data.fechaHora)}</dd>
            </div>
            <div>
              <dt className="font-semibold">Método de pago</dt>
              <dd>{data.metodoPagoNombre}</dd>
            </div>
            <div>
              <dt className="font-semibold">Registrado por</dt>
              <dd>
                {data.usuarioNombre} (#{data.usuarioId})
              </dd>
            </div>
            <div>
              <dt className="font-semibold">Concepto</dt>
              <dd>{data.descripcion}</dd>
            </div>
          </dl>
        ))}
    </>
  );
}

function RutaInvalida() {
  return (
    <EmptyState
      title="Página de caja no encontrada"
      description="Revisá la dirección o volvé a la caja actual."
      action={volver}
    />
  );
}

export function CajaRoutes() {
  return (
    <Routes>
      <Route index element={<CajaActual />} />
      <Route path="historial" element={<HistorialCajas />} />
      <Route path=":id" element={<CajaPorId />} />
      <Route
        path=":id/movimientos/:movimientoId"
        element={<DetalleMovimiento />}
      />
      <Route path="*" element={<RutaInvalida />} />
    </Routes>
  );
}
