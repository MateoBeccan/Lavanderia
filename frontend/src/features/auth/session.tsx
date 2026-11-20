import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError, createApi } from "@/shared/api/http";
import type { ApiClient } from "@/shared/api/http";
import { loginResponse, userResponse } from "./contracts";
import type { LoginRequest, User } from "./contracts";

type Session = { token: string; user: User; expiresAt: number };
type SessionContext = {
  user: User | null;
  expired: boolean;
  api: ApiClient;
  signIn: (credentials: LoginRequest) => Promise<void>;
  signOut: () => void;
};

const SessionContext = createContext<SessionContext | null>(null);
const publicApi = createApi();

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(null);
  const [expired, setExpired] = useState(false);
  const active = useRef<Session | null>(null);

  const clearSession = useCallback(
    (didExpire: boolean) => {
      active.current = null;
      setSession(null);
      setExpired(didExpire);
      queryClient.clear();
    },
    [queryClient],
  );

  const api = useMemo(
    () =>
      createApi(
        () => active.current?.token,
        (token) => {
          if (active.current?.token === token) clearSession(true);
        },
      ),
    [clearSession],
  );

  useEffect(() => {
    if (!session) return;
    let timer: ReturnType<typeof setTimeout>;
    function check() {
      if (!session || active.current !== session) return;
      const remaining = session.expiresAt - Date.now();
      if (remaining <= 0) clearSession(true);
      else timer = setTimeout(check, Math.min(remaining, 2_147_483_647));
    }
    check();
    return () => clearTimeout(timer);
  }, [session, clearSession]);

  async function signIn(credentials: LoginRequest) {
    const issuedAfter = Date.now();
    let response;
    try {
      response = await publicApi<unknown>("/auth/login", {
        method: "POST",
        body: credentials,
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        throw new ApiError(401, "El usuario o la contraseña son incorrectos.");
      }
      throw error;
    }
    const token = loginResponse.parse(response);
    const expiresAt = issuedAfter + token.expiresIn * 1000;
    const me = createApi(() => token.accessToken);
    const user = userResponse.parse(await me<unknown>("/auth/me"));
    if (expiresAt <= Date.now())
      throw new ApiError(401, "La sesión venció. Ingresá nuevamente.");
    const next = { token: token.accessToken, user, expiresAt };
    queryClient.clear();
    active.current = next;
    setExpired(false);
    setSession(next);
  }

  return (
    <SessionContext.Provider
      value={{
        user: session?.user ?? null,
        expired,
        api,
        signIn,
        signOut: () => clearSession(false),
      }}
    >
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) throw new Error("SessionProvider es obligatorio.");
  return context;
}
