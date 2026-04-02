package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlerteDTO {
    private Long id;
    private String niveau;      // CRITIQUE, PREVENTIF, INFO
    private String objetType;
    private Long objetId;
    private String objetLibelle;
    private String description;
    private String dateDetection;
    private String statut;      // ACTIVE, TRAITEE
    private Long chargeMissionId;
    private String chargeMissionNom;
}
