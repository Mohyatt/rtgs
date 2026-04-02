package fr.betuf.rtgs.dto;

import lombok.Data;

@Data
public class AffectationRequestDTO {
    private Long utilisateurId;
    private String role;
    private String motif;
    private Long remplaceLId;
    private boolean forceConflict;
}
