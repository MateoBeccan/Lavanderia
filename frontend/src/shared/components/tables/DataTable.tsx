import { flexRender, tableFeatures, useTable } from "@tanstack/react-table";
import type { ColumnDef, RowData } from "@tanstack/react-table";
import { Button } from "../ui/button";

const features = tableFeatures({});

export function DataTable<T extends RowData>({
  caption,
  columns,
  data,
  getRowId,
  pagination,
}: {
  caption: string;
  columns: ColumnDef<typeof features, T>[];
  data: T[];
  getRowId: (row: T) => string;
  pagination?: {
    pageIndex: number;
    totalPages: number;
    onPageChange: (page: number) => void;
  };
}) {
  const table = useTable({ features, data, columns, getRowId });
  const rows = table.getRowModel().rows;
  return (
    <div className="surface min-w-0">
      <div
        className="overflow-auto"
        role="region"
        aria-label={caption}
        tabIndex={0}
      >
        <table className="data-table">
          <caption>{caption}</caption>
          <thead>
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => (
                  <th
                    key={header.id}
                    scope={header.colSpan > 1 ? "colgroup" : "col"}
                    colSpan={header.colSpan}
                  >
                    {!header.isPlaceholder &&
                      flexRender(
                        header.column.columnDef.header,
                        header.getContext(),
                      )}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {rows.length ? (
              rows.map((row) => (
                <tr key={row.id}>
                  {row.getAllCells().map((cell) => (
                    <td key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </td>
                  ))}
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={table.getAllLeafColumns().length}>
                  Sin resultados.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {pagination && pagination.totalPages > 0 && (
        <nav
          className="flex flex-wrap items-center justify-end gap-2 p-3"
          aria-label="Paginación"
        >
          <span className="text-sm" aria-live="polite">
            Página {pagination.pageIndex + 1} de {pagination.totalPages}
          </span>
          <Button
            variant="outline"
            disabled={pagination.pageIndex === 0}
            onClick={() => pagination.onPageChange(pagination.pageIndex - 1)}
          >
            Anterior
          </Button>
          <Button
            variant="outline"
            disabled={pagination.pageIndex + 1 >= pagination.totalPages}
            onClick={() => pagination.onPageChange(pagination.pageIndex + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}
    </div>
  );
}
