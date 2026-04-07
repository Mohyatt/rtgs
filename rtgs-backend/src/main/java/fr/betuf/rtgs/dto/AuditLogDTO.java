package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private String typeAction;
    private Long idObjet;
    private String typeObjet;
    private String details;
    private String dateHeure;
    private String utilisateurNom;
    private Long utilisateurId;
}
