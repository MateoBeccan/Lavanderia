import { toast } from "sonner";
import { useLocation, useNavigate } from "react-router";
import { ErrorState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { useCambiarProveedor } from "../hooks/queries";
import type { ProveedorResponse } from "../types/proveedores";
import { proveedorError } from "./feedback";

export function DesactivarProveedor({
  proveedor,
  onSuccess,
}: {
  proveedor: ProveedorResponse;
  onSuccess?: () => void;
}) {
  const baja = useCambiarProveedor();
  const navigate = useNavigate();
  const { search } = useLocation();
  const { id, nombre } = proveedor;

  async function desactivar() {
    try {
      await baja.mutateAsync({ tipo: "desactivar", id: String(id) });
      toast.success(`Proveedor #${id} desactivado.`, {
        action: {
          label: "Reactivar",
          onClick: () => {
            const params = new URLSearchParams(search);
            params.set("id", String(id));
            void navigate({
              pathname: "/proveedores/reactivar",
              search: params.toString(),
            });
          },
        },
      });
      onSuccess?.();
    } catch {
      // El error se muestra junto a la acción.
    }
  }

  return (
    <>
      <ConfirmDialog
        trigger={
          <Button
            variant="outline"
            disabled={baja.isPending}
            aria-label={baja.isPending ? undefined : `Desactivar a ${nombre}`}
          >
            {baja.isPending ? "Desactivando…" : "Desactivar"}
          </Button>
        }
        title="Desactivar proveedor"
        description={`${nombre} (#${id}) dejará de aparecer entre los proveedores activos. Se conservarán sus compras y asociaciones.`}
        confirmLabel="Desactivar proveedor"
        onConfirm={() => void desactivar()}
      />
      {baja.isError && <ErrorState message={proveedorError(baja.error)} />}
    </>
  );
}
