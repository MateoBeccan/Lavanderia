import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useRef } from "react";
import { useForm } from "react-hook-form";
import {
  Link,
  useBeforeUnload,
  useBlocker,
  useLocation,
  useNavigate,
} from "react-router";
import { toast } from "sonner";
import { ApiError } from "@/shared/api/http";
import { ErrorState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import type { RegistroCatalogo } from "./api";
import { selectCatalogo } from "./config";
import type { ConfigCatalogo, DatosFormularioCatalogo } from "./config";
import { errorCatalogo } from "./feedback";
import { useModificarCatalogo } from "./queries";

export function CatalogoForm<Request, Response extends RegistroCatalogo>({
  config,
  registro,
}: {
  config: ConfigCatalogo<Request, Response>;
  registro?: Response;
}) {
  const navigate = useNavigate();
  const { search } = useLocation();
  const guardado = useRef(false);
  const enviando = useRef(false);
  const mutation = useModificarCatalogo(config.api);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<DatosFormularioCatalogo, unknown, Request>({
    resolver: zodResolver(config.schema),
    defaultValues: config.valores(registro),
  });
  const blocker = useBlocker(
    () => !guardado.current && (isDirty || enviando.current),
  );
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (
      !enviando.current &&
      window.confirm("¿Salir del formulario y descartar los cambios?")
    )
      blocker.proceed();
    else blocker.reset();
  }, [blocker]);
  useBeforeUnload((event) => {
    if (guardado.current || (!isDirty && !enviando.current)) return;
    event.preventDefault();
    event.returnValue = true;
  });

  const { path } = config.api;
  const id = registro ? String(registro.id) : undefined;
  const guardando = isSubmitting || mutation.isPending;

  async function submit(body: Request) {
    if (enviando.current) return;
    enviando.current = true;
    try {
      const saved = await mutation.mutateAsync({ tipo: "guardar", body, id });
      if (!saved) throw new Error("Falta el registro guardado.");
      guardado.current = true;
      toast.success(
        `${config.singular === "prenda" ? "Prenda" : "Servicio"} ${registro ? "actualizad" : "registrad"}${config.genero}.`,
      );
      await navigate(
        { pathname: `${path}/${saved.id}`, search },
        { replace: true },
      );
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 409 && /con ese nombre/i.test(error.message)) {
          setError("nombre", { message: error.message }, { shouldFocus: true });
          return;
        }
        const fields = Object.keys(config.valores(registro));
        const conocidos = error.fields.filter(({ campo }) =>
          fields.includes(campo),
        );
        for (const { campo, mensaje } of conocidos) {
          setError(campo as keyof DatosFormularioCatalogo, {
            message: mensaje,
          });
        }
        if (conocidos.length && conocidos.length === error.fields.length)
          return;
      }
      setError("root", { message: errorCatalogo(error) });
    } finally {
      enviando.current = false;
    }
  }

  function atributos(name: keyof DatosFormularioCatalogo) {
    return {
      id: name,
      required: true,
      "aria-invalid": !!errors[name],
      "aria-describedby": errors[name] ? `${name}-error` : undefined,
      ...register(name),
    };
  }

  return (
    <form
      className="surface p-4 sm:p-6"
      noValidate
      onSubmit={handleSubmit(submit)}
    >
      <fieldset
        disabled={guardando}
        className="grid min-w-0 gap-5 sm:grid-cols-2"
      >
        <FormField id="nombre" label="Nombre" error={errors.nombre?.message}>
          <Input {...atributos("nombre")} />
        </FormField>
        {config.modalidades && (
          <>
            <FormField
              id="precio"
              label="Precio"
              error={errors.precio?.message}
            >
              <Input inputMode="decimal" {...atributos("precio")} />
            </FormField>
            <FormField
              id="modalidadCobro"
              label="Modalidad de cobro"
              error={errors.modalidadCobro?.message}
            >
              <select
                className={selectCatalogo}
                {...atributos("modalidadCobro")}
              >
                <option value="">Seleccioná una modalidad</option>
                {config.modalidades.map((modalidad) => (
                  <option key={modalidad}>{modalidad}</option>
                ))}
              </select>
            </FormField>
          </>
        )}
      </fieldset>
      {errors.root?.message && (
        <div className="mt-5">
          <ErrorState message={errors.root.message} />
        </div>
      )}
      <div className="mt-6 flex flex-wrap gap-3">
        <Button type="submit" disabled={guardando}>
          {guardando ? "Guardando…" : `Guardar ${config.singular}`}
        </Button>
        {!guardando && (
          <Button asChild variant="outline">
            <Link to={{ pathname: id ? `${path}/${id}` : path, search }}>
              Cancelar
            </Link>
          </Button>
        )}
      </div>
    </form>
  );
}
