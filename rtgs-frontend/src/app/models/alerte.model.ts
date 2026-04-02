export interface AlerteDTO {
  id: number;
  niveau: 'CRITIQUE' | 'PREVENTIF' | 'INFO';
  objetType: string;
  objetId: number;
  objetLibelle: string;
  description: string;
  dateDetection: string;
  statut: 'ACTIVE' | 'TRAITEE';
}

export interface AlerteAssignmentDTO {
  id: number;
  alerteAuditLogId: number;
  niveau: string;
  objetType: string;
  objetId: number;
  objetLibelle: string;
  description: string;
  commentaire: string;
  statut: 'ASSIGNEE' | 'TRAITEE';
  createdAt: string;
  traiteAt?: string;
  chargeMissionId: number;
  chargeMissionNom: string;
  assigneParId?: number;
  assigneParNom?: string;
  interventionId?: number;
  interventionReference?: string;
}

export interface CdmPerformanceDTO {
  alertesTotales: number;
  alertesEnAttente: number;
  alertesTraitees: number;
  tempsTraitementMoyenHeures: number;
  tauxTraitement: number;
}
