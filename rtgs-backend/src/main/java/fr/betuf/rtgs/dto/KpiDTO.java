package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiDTO {
    private int alertesCritiques;
    private int alertesPreventives;
    private int alertesInfo;
    private int interventionsEnRetard;
    private int interventionsCeMois;
    private int tauxConformite;
    private int tunnelsActifs;
    private int tunnelsConformes;
    private int tunnelsAPlanifier;
    private int tunnelsEnRetard;
    private int totalTunnels;
}
