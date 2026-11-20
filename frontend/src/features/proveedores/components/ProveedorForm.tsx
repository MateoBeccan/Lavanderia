import { zodResolver } from "@hookform/resolvers/zod";
import { useRef } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import { ApiError } from "@/shared/api/http";
import { ErrorState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { useUnsavedChanges } from "@/shared/components/forms/useUnsavedChanges";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { useCambiarProveedor } from "../hooks/queries";
import { proveedorSchema } from "../schemas/proveedor";
import type { ProveedorFormValues } from "../schemas/proveedor";
import type { ProveedorRequest, ProveedorResponse } from "../types/proveedores";
import { proveedorError } from "./feedback";

const fields = [
  {
    name: "nombre",
    label: "Nombre",
    required: true,
    autoComplete: "organization",
  },
  { name: "cuit", label: "CUIT", required: true, placeholder: "XX-XXXXXXXX-X" },
  {
    name: "telefono",
    label: "Teléfono",
    required: true,
    type: "tel",
    autoComplete: "tel",
  },
  { name: "rubro", label: "Rubro (opcional)" },
] as const;

export function ProveedorForm({
  proveedor,
}: {
  proveedor?: ProveedorResponse;
}) {
  const navigate = useNavigate();
  const { search } = useLocation();
  const guardado = useRef(false);
  const enviando = useRef(false);
  const mutation = useCambiarProveedor();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<ProveedorFormValues, unknown, ProveedorRequest>({
    resolver: zodResolver(proveedorSchema),
    defaultValues: {
      nombre: proveedor?.nombre ?? "",
      cuit: proveedor?.cuit ?? "",
      telefono: proveedor?.telefono ?? "",
      rubro: proveedor?.rubro ?? "",
    },
  });
  useUnsavedChanges(isDirty, enviando, guardado);
  const id = proveedor ? String(proveedor.id) : undefined;
  const guardando = isSubmitting || mutation.isPending;

  async function submit(body: ProveedorRequest) {
    if (enviando.current) return;
    enviando.current = true;
    try {
      const saved = await mutation.mutateAsync({ tipo: "guardar", body, id });
      if (!saved) throw new Error("Falta el proveedor guardado.");
      guardado.current = true;
      toast.success(id ? "Proveedor actualizado." : "Proveedor registrado.");
      await navigate(
        { pathname: `/proveedores/${saved.id}`, search },
        { replace: true },
      );
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 409 && /\bCUIT\b/i.test(error.message)) {
          setError("cuit", { message: error.message });
          return;
        }
        const conocidos = error.fields.filter(({ campo }) =>
          fields.some(({ name }) => name === campo),
        );
        for (const { campo, mensaje } of conocidos) {
          setError(campo as keyof ProveedorFormValues, { message: mensaje });
        }
        if (conocidos.length && conocidos.length === error.fields.length)
          return;
      }
      setError("root", { message: proveedorError(error) });
    } finally {
      enviando.current = false;
    }
  }

  return (
    <form
      noValidate
      onSubmit={handleSubmit(submit)}
      className="surface p-4 sm:p-6"
    >
      <fieldset
        disabled={guardando}
        className="grid min-w-0 gap-5 sm:grid-cols-2"
      >
        {fields.map(({ name, label, ...props }) => (
          <FormField
            key={name}
            id={name}
            label={label}
            error={errors[name]?.message}
          >
            <Input
              {...props}
              id={name}
              aria-invalid={!!errors[name]}
              aria-describedby={errors[name] ? `${name}-error` : undefined}
              {...register(name)}
            />
          </FormField>
        ))}
      </fieldset>
      {errors.root?.message && (
        <div className="mt-5">
          <ErrorState message={errors.root.message} />
        </div>
      )}
      <div className="mt-6 flex flex-wrap gap-3">
        <Button type="submit" disabled={guardando}>
          {guardando ? "Guardando…" : "Guardar proveedor"}
        </Button>
        {!guardando && (
          <Button asChild variant="outline">
            <Link
              to={{
                pathname: id ? `/proveedores/${id}` : "/proveedores",
                search,
              }}
            >
              Cancelar
            </Link>
          </Button>
        )}
      </div>
    </form>
  );
}
