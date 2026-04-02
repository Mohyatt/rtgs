export interface UtilisateurDTO {
  id: number;
  nomComplet: string;
  email: string;
  role: string;
  statut: string;
  competences: string;
  pole: string;
}

export interface UtilisateurDispoDTO extends UtilisateurDTO {
  competencesList: string[];
  disponibilite: 'DISPONIBLE' | 'CONFLIT' | 'INDISPONIBLE';
  nbMissionsActives: number;
}
