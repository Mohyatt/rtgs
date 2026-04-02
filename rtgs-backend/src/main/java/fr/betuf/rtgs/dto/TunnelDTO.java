package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TunnelDTO {
    private Long id;
    private String libelle;
    private String departement;
    private Double longueur;
    private Integer nbTubes;
    private String statut;
    private String type;
    private String dateDerniereVisite;
    private String exploitant;
}
