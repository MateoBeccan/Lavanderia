import { NavLink } from "react-router";
import type { Role } from "@/features/auth/contracts";
import { canAccess, groups } from "@/app/router/navigation";

export function Sidebar({
  role,
  onNavigate,
}: {
  role: Role;
  onNavigate?: () => void;
}) {
  return (
    <div className="sidebar-content">
      <nav aria-label="Navegación principal">
        {groups.map((group) => {
          const items = group.items.filter((item) => canAccess(item, role));
          if (!items.length) return null;
          return (
            <div className="nav-group" key={group.label}>
              <p>{group.label}</p>
              <ul>
                {items.map(({ path, label, pending, icon: Icon }) => (
                  <li key={path}>
                    <NavLink
                      end={path === "/"}
                      className="sidebar-link"
                      to={path}
                      onClick={onNavigate}
                    >
                      <Icon size={18} aria-hidden="true" />
                      <span className="min-w-0 flex-1">{label}</span>
                      {pending && (
                        <small className="text-xs text-muted-foreground">
                          Pendiente
                        </small>
                      )}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
      </nav>
    </div>
  );
}
