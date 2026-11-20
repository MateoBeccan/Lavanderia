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
import { useGuardarCliente } from "../hooks/queries";
import { clienteSchema } from "../schemas/cliente";
import type { ClienteFormValues } from "../schemas/cliente";
import type { ClienteRequest, ClienteResponse } from "../types/clientes";
import { clienteError } from "./feedback";

const fields = [
  {
    name: "nombre",
    label: "Nombre",
    required: true,
    autoComplete: "given-name",
  },
  {
    name: "apellido",
    label: "Apellido",
    required: true,
    autoComplete: "family-name",
  },
  { name: "dni", label: "DNI", required: true, inputMode: "numeric" },
  {
    name: "telefono",
    label: "Teléfono (opcional)",
    type: "tel",
    autoComplete: "tel",
  },
  {
    name: "email",
    label: "Correo electrónico (opcional)",
    type: "email",
    autoComplete: "email",
  },
] as const;

export function ClienteForm({ cliente }: { cliente?: ClienteResponse }) {
  const navigate = useNavigate();
  const { search } = useLocation();
  const guardado = useRef(false);
  const enviando = useRef(false);
  const mutation = useGuardarCliente(cliente ? String(cliente.id) : undefined);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<ClienteFormValues, unknown, ClienteRequest>({
    resolver: zodResolver(clienteSchema),
    defaultValues: {
      nombre: cliente?.nombre ?? "",
      apellido: cliente?.apellido ?? "",
      dni: cliente?.dni ?? "",
      telefono: cliente?.telefono ?? "",
      email: cliente?.email ?? "",
    },
  });
  useUnsavedChanges(isDirty, enviando, guardado);

  const volver = {
    pathname: cliente ? `/clientes/${cliente.id}` : "/clientes",
    search,
  };
  const guardando = isSubmitting || mutation.isPending;

  async function submit(values: ClienteRequest) {
    if (enviando.current) return;
    enviando.current = true;
    try {
      const saved = await mutation.mutateAsync(values);
      guardado.current = true;
      toast.success(cliente ? "Cliente actualizado." : "Cliente registrado.");
      await navigate(
        { pathname: `/clientes/${saved.id}`, search },
        { replace: true },
      );
    } catch (error) {
      let hasFieldError = false;
      if (error instanceof ApiError) {
        for (const { campo, mensaje } of error.fields) {
          const field = fields.find(({ name }) => name === campo);
          if (field) {
            setError(field.name, { message: mensaje });
            hasFieldError = true;
          }
        }
        if (error.status === 409 && /\bDNI\b/i.test(error.message)) {
          setError("dni", { message: clienteError(error) });
          hasFieldError = true;
        }
      }
      if (!hasFieldError) {
        setError("root", { message: clienteError(error) });
      }
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
        {fields.map(({ name, label, ...inputProps }) => (
          <FormField
            key={name}
            id={name}
            label={label}
            error={errors[name]?.message}
          >
            <Input
              {...inputProps}
              id={name}
              aria-invalid={!!errors[name]}
              aria-describedby={errors[name] ? name + "-error" : undefined}
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
          {guardando ? "Guardando…" : "Guardar cliente"}
        </Button>
        {!guardando && (
          <Button asChild variant="outline">
            <Link to={volver}>Cancelar</Link>
          </Button>
        )}
      </div>
    </form>
  );
}
