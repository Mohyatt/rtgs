package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlerteAssignmentDTO {
    private Long id;
    private Long alerteAuditLogId;
    private String niveau;
    private String objetType;
    private Long objetId;
    private String objetLibelle;
    private String description;
    private String commentaire;
    private String statut;
    private String createdAt;
    private String traiteAt;
    private Long chargeMissionId;
    private String chargeMissionNom;
    private Long assigneParId;
    private String assigneParNom;
    private Long interventionId;
    private String interventionReference;
}
