import {
  LayoutDashboard,
  UsersRound,
  ClipboardList,
  ClipboardPlus,
  Banknote,
  Wallet,
  History,
  Truck,
  Package,
  ShoppingCart,
  HandCoins,
  ReceiptText,
  ChartNoAxesCombined,
  Settings,
  Shirt,
  WashingMachine,
  Tags,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { Role } from "@/features/auth/contracts";

export type NavItem = {
  path: string;
  label: string;
  icon: LucideIcon;
  admin?: boolean;
  pending?: boolean;
};

type NavGroup = { label: string; items: NavItem[] };

const orders = { path: "/pedidos", label: "Pedidos", icon: ClipboardList };
const cash = { path: "/caja", label: "Caja", icon: Wallet };
const payments = { path: "/cobros", label: "Cobros", icon: Banknote };

const operations: NavGroup = {
  label: "Acceso rápido",
  items: [orders, cash, payments],
};

export const administrationGroups: NavGroup[] = [
  {
    label: "Clientes y catálogos",
    items: [
      { path: "/clientes", label: "Clientes", icon: UsersRound },
      { path: "/servicios", label: "Servicios", icon: WashingMachine },
      { path: "/prendas", label: "Prendas", icon: Shirt },
    ],
  },
  {
    label: "Abastecimiento",
    items: [
      { path: "/proveedores", label: "Proveedores", icon: Truck },
      { path: "/insumos", label: "Insumos", icon: Package },
      {
        path: "/insumos/categorias",
        label: "Categorías de insumos",
        icon: Tags,
      },
      { path: "/compras", label: "Compras", icon: ShoppingCart },
      {
        path: "/pagos-proveedor",
        label: "Pagos a proveedores",
        icon: HandCoins,
      },
    ],
  },
  {
    label: "Control y administración",
    items: [
      {
        path: "/comprobantes",
        label: "Comprobantes",
        icon: ReceiptText,
      },
      {
        path: "/reportes",
        label: "Reportes",
        icon: ChartNoAxesCombined,
        admin: true,
      },
      {
        path: "/administracion",
        label: "Usuarios y administración",
        icon: Settings,
        admin: true,
        pending: true,
      },
    ],
  },
];

export const groups: NavGroup[] = [
  {
    label: "General",
    items: [{ path: "/", label: "Inicio", icon: LayoutDashboard }],
  },
  operations,
  {
    label: "Gestión administrativa",
    items: administrationGroups.flatMap((group) => group.items),
  },
];

export const newOrder: NavItem = {
  path: "/pedidos/nuevo",
  label: "Nuevo pedido",
  icon: ClipboardPlus,
};

export const dashboardActions: (NavItem & { key: string })[] = [
  { ...newOrder, key: "N" },
  { ...orders, label: "Ver pedidos", key: "P" },
  { ...payments, label: "Registrar cobro", key: "C" },
  { ...cash, key: "J" },
];

export const navigation = groups.flatMap((group) => group.items);
export const moduleDestinations: NavItem[] = [
  newOrder,
  ...navigation,
  {
    path: "/caja/historial",
    label: "Historial de caja",
    icon: History,
    admin: true,
  },
].filter(({ pending }) => !pending);
export const canAccess = (item: NavItem, role: Role) =>
  !item.admin || role === "ADMINISTRADOR";
