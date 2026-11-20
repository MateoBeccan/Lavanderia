const logoUrl = import.meta.env.BASE_URL + "images/logo-lavanderia.png";

export function Brand({ showSubtitle = false }: { showSubtitle?: boolean }) {
  return (
    <div className="brand">
      <img className="brand-logo" src={logoUrl} alt="" />
      <span className="brand-copy">
        <strong>MIRÁ QUE TE LAVO</strong>
        {showSubtitle && <span>Sistema de Gestión de Lavandería</span>}
      </span>
    </div>
  );
}
