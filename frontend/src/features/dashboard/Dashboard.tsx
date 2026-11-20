import { ClipboardList, UsersRound, Wallet } from "lucide-react";

import type { ReactNode } from "react";
import { DashboardNavigation } from "./DashboardNavigation";
import { roleLabels } from "@/features/auth/contracts";
import { useSession } from "@/features/auth/session";
import { PageHeader } from "@/shared/components/PageHeader";
import { StatCard } from "@/shared/components/StatCard";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { ApiError } from "@/shared/api/http";
import { useDashboardQueries } from "./queries";

const numberFormat = new Intl.NumberFormat("es-AR");
function Metric({
  query,
  children,
}: {
  query: {
    isPending: boolean;
    error: Error | null;
    refetch: () => Promise<unknown>;
  };
  children?: ReactNode;
}) {
  if (query.isPending) return <LoadingState />;
  if (query.error) {
    const forbidden =
      query.error instanceof ApiError && query.error.status === 403;
    return (
      <ErrorState
        message={
          forbidden ? "Acceso restringido." : "No se pudo cargar el dato."
        }
        onRetry={
          forbidden
            ? undefined
            : () => {
                void query.refetch();
              }
        }
      />
    );
  }
  return children;
}

export function Dashboard() {
  const { user } = useSession();
  const { clientes, pedidos, caja } = useDashboardQueries();
  if (!user) return null;
  const role = user.rol;

  return (
    <div className="dashboard">
      <PageHeader
        title={`Bienvenido, ${user.nombre.trim() || roleLabels[role]}`}
      />
      <DashboardNavigation role={role} />

      <section className="dashboard-section" aria-labelledby="metrics-title">
        <h2 id="metrics-title">Estado de la operación</h2>
        <div className="dashboard-metrics">
          <StatCard
            title="Estado de caja"
            icon={Wallet}
            className="dashboard-cash"
            footnote={
              caja.isSuccess
                ? caja.data
                  ? `Caja #${caja.data.id}`
                  : "Abrí caja para registrar cobros"
                : undefined
            }
          >
            <Metric query={caja}>
              <span
                className={
                  caja.data
                    ? "cash-status cash-status-open"
                    : "cash-status cash-status-closed"
                }
              >
                {caja.data ? "Abierta" : "Sin caja abierta"}
              </span>
            </Metric>
          </StatCard>
          <StatCard
            title="Clientes activos"
            icon={UsersRound}
            footnote="Total de clientes habilitados"
          >
            <Metric query={clientes}>
              {clientes.data === undefined
                ? null
                : numberFormat.format(clientes.data)}
            </Metric>
          </StatCard>
          <StatCard
            title="Pedidos registrados"
            icon={ClipboardList}
            footnote="Todos los estados y fechas"
          >
            <Metric query={pedidos}>
              {pedidos.data === undefined
                ? null
                : numberFormat.format(pedidos.data)}
            </Metric>
          </StatCard>
        </div>
      </section>
    </div>
  );
}
