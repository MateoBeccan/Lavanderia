export function EstadoActivo({ activo }: { activo: boolean }) {
  return (
    <span
      className={
        activo
          ? "inline-flex rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-800"
          : "text-sm text-muted-foreground"
      }
    >
      {activo ? "Activo" : "Inactivo"}
    </span>
  );
}
