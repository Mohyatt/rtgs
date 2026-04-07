package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloturerResponse {
    private InterventionDTO intervention;
    private String referenceCorrectif;
    private Long correctifId;
}
