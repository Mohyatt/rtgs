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
public class ConflitPlanningDTO {
    private boolean aConflit;
    private InterventionDTO interventionEnConflit;
    private String optionDecaler;
    private List<UtilisateurDispoDTO> alternatives;
}
