import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";
import "@fontsource-variable/inter";
import "@/styles/index.css";
import { AppProviders } from "@/app/providers/AppProviders";
import { AppRouter } from "@/app/router/AppRouter";

const router = createBrowserRouter([{ path: "*", element: <AppRouter /> }]);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
);
