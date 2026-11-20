import { zodResolver } from "@hookform/resolvers/zod";
import { useRef } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { ApiError } from "@/shared/api/http";
import { PageHeader } from "@/shared/components/PageHeader";
import { ErrorState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { Input } from "@/shared/components/ui/input";
import { proveedorError } from "../components/feedback";
import { useCambiarProveedor } from "../hooks/queries";
import { reactivacionSchema } from "../schemas/proveedor";

export function ReactivarProveedorPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const activa = useCambiarProveedor();
  const enviando = useRef(false);
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    trigger,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<{ id: string }>({
    resolver: zodResolver(reactivacionSchema),
    defaultValues: { id: params.get("id") ?? "" },
  });
  const confirmacion = useRef<HTMLButtonElement>(null);
  const id = watch("id").trim();
  const listado = new URLSearchParams(params);
  listado.delete("id");
  const search = listado.toString();
  const guardando = activa.isPending || isSubmitting;

  async function activar() {
    await handleSubmit(async ({ id }) => {
      if (enviando.current) return;
      enviando.current = true;
      try {
        const proveedor = await activa.mutateAsync({ tipo: "activar", id });
        if (!proveedor) throw new Error("Falta el proveedor reactivado.");
        toast.success(`Proveedor #${proveedor.id} activo.`);
        await navigate(
          { pathname: `/proveedores/${proveedor.id}`, search },
          { replace: true },
        );
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          setError("id", { message: "No existe un proveedor con ese ID." });
        } else {
          setError("root", { message: proveedorError(error) });
        }
      } finally {
        enviando.current = false;
      }
    })();
  }

  return (
    <>
      <PageHeader
        title="Reactivar proveedor"
        description="Usá el ID del proveedor para volver a habilitarlo."
      />
      <form
        className="surface grid gap-5 p-4 sm:p-6"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          confirmacion.current?.click();
        }}
      >
        <FormField
          id="proveedor-reactivar-id"
          label="ID del proveedor"
          error={errors.id?.message}
        >
          <Input
            id="proveedor-reactivar-id"
            inputMode="numeric"
            required
            disabled={guardando}
            aria-invalid={!!errors.id}
            aria-describedby={
              errors.id ? "proveedor-reactivar-id-error" : undefined
            }
            {...register("id", { onChange: () => clearErrors() })}
          />
        </FormField>
        {errors.root?.message && <ErrorState message={errors.root.message} />}
        <div className="flex flex-wrap gap-3">
          <ConfirmDialog
            trigger={
              <Button
                ref={confirmacion}
                type="button"
                disabled={guardando}
                onClick={(event) => {
                  if (reactivacionSchema.safeParse({ id }).success) return;
                  event.preventDefault();
                  void trigger("id", { shouldFocus: true });
                }}
              >
                {guardando ? "Reactivando…" : "Revisar reactivación"}
              </Button>
            }
            title="Confirmar reactivación"
            description={`El proveedor #${id} quedará activo y volverá a aparecer en el listado.`}
            confirmLabel="Reactivar proveedor"
            onConfirm={() => void activar()}
          />
          <Button asChild variant="outline">
            <Link to={{ pathname: "/proveedores", search }}>
              Volver a proveedores
            </Link>
          </Button>
        </div>
      </form>
    </>
  );
}
