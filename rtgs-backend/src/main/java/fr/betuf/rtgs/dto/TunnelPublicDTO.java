package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TunnelPublicDTO {
    private Long id;
    private String libelle;
    private String type;
    private Double longueur;
    private String departement;
    private String dateDerniereVisite;
    private String statutConformite; // CONFORME, A_PLANIFIER, EN_RETARD
}
