import {
  Eye,
  EyeOff,
  LoaderCircle,
  LockKeyhole,
  MoveRight,
  UserRound,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Brand } from "@/shared/components/Brand";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { FormField } from "@/shared/components/forms/FormField";
import { ErrorState } from "@/shared/components/feedback/States";
import { ApiError } from "@/shared/api/http";
import { loginSchema } from "./contracts";
import type { LoginRequest } from "./contracts";
import { useSession } from "./session";

export function LoginPage() {
  const { signIn, expired } = useSession();
  const [visible, setVisible] = useState(false);
  const title = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    document.title = "Iniciar sesión | Mira que te lavo";
    title.current?.focus();
  }, []);
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    resetField,
    formState: { errors, isSubmitting },
  } = useForm<LoginRequest>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  });

  async function submit(credentials: LoginRequest) {
    clearErrors("root");
    try {
      await signIn(credentials);
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : "El servidor devolvió una respuesta inesperada.";
      const fields = error instanceof ApiError ? error.fields : [];
      let fieldError = false;
      for (const field of fields) {
        if (field.campo === "username" || field.campo === "password") {
          setError(
            field.campo,
            { message: field.mensaje },
            { shouldFocus: !fieldError },
          );
          fieldError = true;
        }
      }
      if (!fieldError) setError("root", { message });
      if (error instanceof ApiError && error.status === 401)
        resetField("password");
    }
  }

  return (
    <main className="login-layout">
      <div className="login-brand">
        <Brand showSubtitle />
        <div className="login-illustration" aria-hidden="true">
          <div className="login-drum">
            <div className="login-drum-glass" />
          </div>
        </div>
        <p className="login-brand-caption">
          Pedidos, clientes y caja en un solo lugar.
        </p>
      </div>
      <section className="login-content" aria-labelledby="login-title">
        <div className="login-form-wrap">
          <span className="icon-tile login-lock" aria-hidden="true">
            <LockKeyhole size={22} />
          </span>
          <h1 id="login-title" ref={title} tabIndex={-1}>
            Bienvenido de nuevo
          </h1>
          <p className="login-intro">Ingresá tus credenciales para continuar</p>
          {expired && (
            <p role="status" className="notice mb-5">
              Tu sesión venció. Volvé a ingresar para continuar.
            </p>
          )}
          <form
            noValidate
            onSubmit={handleSubmit(submit)}
            className="login-form"
          >
            <FormField
              id="username"
              label="Usuario"
              error={errors.username?.message}
            >
              <div className="relative">
                <UserRound
                  className="login-field-icon"
                  size={18}
                  aria-hidden="true"
                />
                <Input
                  id="username"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  className="min-h-12 border-field-border pl-10"
                  aria-invalid={!!errors.username}
                  aria-describedby={
                    errors.username ? "username-error" : undefined
                  }
                  required
                  {...register("username")}
                />
              </div>
            </FormField>
            <FormField
              id="password"
              label="Contraseña"
              error={errors.password?.message}
            >
              <div className="relative">
                <LockKeyhole
                  className="login-field-icon"
                  size={18}
                  aria-hidden="true"
                />
                <Input
                  id="password"
                  type={visible ? "text" : "password"}
                  autoComplete="current-password"
                  className="min-h-12 border-field-border pr-12 pl-10"
                  aria-invalid={!!errors.password}
                  aria-describedby={
                    errors.password ? "password-error" : undefined
                  }
                  required
                  {...register("password")}
                />
                <button
                  className="password-toggle"
                  type="button"
                  aria-controls="password"
                  aria-label={
                    visible ? "Ocultar contraseña" : "Mostrar contraseña"
                  }
                  onClick={() => setVisible(!visible)}
                >
                  {visible ? (
                    <EyeOff size={19} aria-hidden="true" />
                  ) : (
                    <Eye size={19} aria-hidden="true" />
                  )}
                </button>
              </div>
            </FormField>
            {errors.root?.message && (
              <div className="login-error">
                <ErrorState message={errors.root.message} />
              </div>
            )}
            <Button
              type="submit"
              disabled={isSubmitting}
              className="login-submit min-h-12 w-full"
            >
              {isSubmitting ? (
                <>
                  <LoaderCircle
                    size={17}
                    className="motion-safe:animate-spin"
                    aria-hidden="true"
                  />
                  Ingresando…
                </>
              ) : (
                <>
                  Iniciar sesión <MoveRight size={17} aria-hidden="true" />
                </>
              )}
            </Button>
            <span role="status" className="sr-only">
              {isSubmitting ? "Verificando tu cuenta" : ""}
            </span>
          </form>
        </div>
      </section>
    </main>
  );
}
