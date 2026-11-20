import { LogOut } from "lucide-react";
import { toast } from "sonner";
import { useSession } from "@/features/auth/session";
import { roleLabels } from "@/features/auth/contracts";
import type { User } from "@/features/auth/contracts";

export function UserMenu({ user }: { user: User }) {
  const { signOut } = useSession();
  const initials = user.nombre
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((name) => name[0])
    .join("")
    .toUpperCase();
  return (
    <div className="header-account">
      <div className="header-identity">
        <span className="avatar" aria-hidden="true">
          {initials}
        </span>
        <span className="user-summary">
          <strong>{user.nombre}</strong>
          <small>{roleLabels[user.rol]}</small>
        </span>
      </div>
      <button
        className="sign-out-button"
        type="button"
        onClick={() => {
          signOut();
          toast.success("Sesión cerrada");
        }}
      >
        <LogOut size={17} aria-hidden="true" /> Cerrar sesión
      </button>
    </div>
  );
}
