package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffectationResponseDTO {
    private Long id;
    private Long utilisateurId;
    private String nomComplet;
    private String role;
    private String pole;
    private String competences;
}
