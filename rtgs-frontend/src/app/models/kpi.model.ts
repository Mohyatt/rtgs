import { InterventionDTO } from './intervention.model';
import { UtilisateurDispoDTO } from './utilisateur.model';

export interface KpiDTO {
  alertesCritiques: number;
  alertesPreventives: number;
  alertesInfo: number;
  interventionsEnRetard: number;
  interventionsCeMois: number;
  tauxConformite: number;
  tunnelsActifs: number;
  tunnelsConformes: number;
  tunnelsAPlanifier: number;
  tunnelsEnRetard: number;
  totalTunnels: number;
}

export interface ChargeIngenieurDTO {
  id: number;
  nomComplet: string;
  pole: string;
  competences: string;
  nbMissionsActives: number;
  niveau: 'OK' | 'MOYEN' | 'ELEVE';
  statut: string;
}

export interface ConflitPlanningDTO {
  aConflit: boolean;
  interventionEnConflit?: InterventionDTO;
  optionDecaler?: string;
  alternatives?: UtilisateurDispoDTO[];
}
