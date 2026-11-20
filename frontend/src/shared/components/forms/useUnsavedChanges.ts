import { useEffect } from "react";
import type { RefObject } from "react";
import { useBeforeUnload, useBlocker } from "react-router";

export function useUnsavedChanges(
  isDirty: boolean,
  saving: RefObject<boolean>,
  saved: RefObject<boolean>,
) {
  const blocker = useBlocker(
    () => !saved.current && (isDirty || saving.current),
  );
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (
      !saving.current &&
      window.confirm("¿Salir del formulario y descartar los cambios?")
    ) {
      blocker.proceed();
    } else {
      blocker.reset();
    }
  }, [blocker, saving]);

  useBeforeUnload((event) => {
    if (saved.current || (!isDirty && !saving.current)) return;
    event.preventDefault();
    event.returnValue = true;
  });
}
