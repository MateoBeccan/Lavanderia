import { Button } from "@/shared/components/ui/button";

export function SelectorPagina({
  nombre,
  page,
  total,
  cambiar,
}: {
  nombre: string;
  page: number;
  total: number;
  cambiar: (page: number) => void;
}) {
  if (total <= 1 && page === 0) return null;
  return (
    <nav
      aria-label={`Páginas de ${nombre}`}
      className="flex flex-wrap items-center gap-2"
    >
      <Button
        type="button"
        variant="outline"
        className="px-3 text-sm"
        disabled={page === 0}
        onClick={() => cambiar(page - 1)}
      >
        Anterior
      </Button>
      <span className="text-sm" aria-live="polite">
        Página {page + 1}
        {total > 0 && ` de ${total}`}
      </span>
      <Button
        type="button"
        variant="outline"
        className="px-3 text-sm"
        disabled={page + 1 >= total}
        onClick={() => cambiar(page + 1)}
      >
        Siguiente
      </Button>
    </nav>
  );
}
