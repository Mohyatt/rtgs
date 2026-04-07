import { TunnelDTO } from './tunnel.model';

export interface AffectationResponseDTO {
  id: number;
  utilisateurId: number;
  nomComplet: string;
  role: string;
  pole: string;
  competences: string;
  statut: string;
}

export interface AffectationRequestDTO {
  utilisateurId: number;
  role: string;
  motif?: string;
  remplaceLId?: number;
  forceConflict?: boolean;
}

export interface NonConformiteDTO {
  id?: number;
  gravite: 'MINEUR' | 'MAJEUR' | 'CRITIQUE';
  description: string;
  statut?: string;
  delaiCorrectionJours?: number;
  declarantId?: number;
}

export interface InterventionDTO {
  id: number;
  reference: string;
  tunnel: TunnelDTO;
  type: string;
  typeLibelle: string;
  statut: string;
  datePrevue: string;
  dateFinPrevue?: string;
  dateRealisation?: string;
  competencesRequises: string;
  description?: string;
  resume?: string;
  recommandations?: string;
  affectations: AffectationResponseDTO[];
  nonConformites: NonConformiteDTO[];
  createdAt?: string;
  createurId?: number;
  createurNom?: string;
  monRole?: string;
}

export interface CloturerRequest {
  dateRealisation: string;
  resume: string;
  recommandations?: string;
  nonConformites: NonConformiteDTO[];
}

export interface CloturerResponse {
  intervention: InterventionDTO;
  referenceCorrectif?: string;
  correctifId?: number;
}
