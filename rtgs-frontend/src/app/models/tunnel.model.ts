export interface TunnelDTO {
  id: number;
  libelle: string;
  departement: string;
  longueur: number;
  nbTubes: number;
  statut: string;
  type: string;
  dateDerniereVisite: string;
  exploitant: string;
}

export interface TunnelPublicDTO {
  id: number;
  libelle: string;
  type: string;
  longueur: number;
  departement: string;
  dateDerniereVisite: string;
  statutConformite: 'CONFORME' | 'A_PLANIFIER' | 'EN_RETARD';
}
