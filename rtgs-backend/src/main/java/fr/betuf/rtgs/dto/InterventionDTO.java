package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionDTO {
    private Long id;
    private String reference;
    private TunnelDTO tunnel;
    private String type;
    private String typeLibelle;
    private String statut;
    private String datePrevue;
    private String dateFinPrevue;
    private String dateRealisation;
    private String competencesRequises;
    private String description;
    private String resume;
    private String recommandations;
    private List<AffectationResponseDTO> affectations;
    private List<NonConformiteDTO> nonConformites;
    private String createdAt;
    private Long createurId;
    private String createurNom;
    private String monRole;
}
