package fr.betuf.rtgs.service;

import fr.betuf.rtgs.entity.Intervention;
import fr.betuf.rtgs.entity.Tunnel;
import fr.betuf.rtgs.entity.enums.*;
import fr.betuf.rtgs.repository.InterventionRepository;
import fr.betuf.rtgs.repository.TunnelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanificationAutoService {

    private final TunnelRepository tunnelRepository;
    private final InterventionRepository interventionRepository;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Scheduled(cron = "0 0 6 1 * *")
    public int planifierVisitesPeriodiques() {
        log.info("Planification automatique des visites périodiques...");
        int count = 0;
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(60);

        for (Tunnel tunnel : tunnelRepository.findByStatutAndLongueurGreaterThan(TunnelStatut.ACTIF, 1000.0)) {
            if (tunnel.getDateDerniereVisite() == null) continue;

            LocalDate echeance = tunnel.getDateDerniereVisite().plusDays(365);
            if (echeance.isAfter(horizon)) continue;

            // Check if an active intervention already planned
            List<Intervention> existing = interventionRepository.findActiveByTunnelAndTypeInPeriod(
                    tunnel.getId(), InterventionType.VISITE_SECURITE_ANNUELLE, today, horizon);

            if (!existing.isEmpty()) continue;

            LocalDate datePrevue = echeance.isAfter(today.plusDays(2)) ? echeance : today.plusDays(2);

            Intervention intervention = Intervention.builder()
                    .tunnel(tunnel)
                    .type(InterventionType.VISITE_SECURITE_ANNUELLE)
                    .statut(InterventionStatut.BROUILLON)
                    .datePrevue(datePrevue)
                    .competencesRequises("SECURITE,VENTILATION")
                    .description("Visite de sécurité annuelle obligatoire")
                    .build();
            intervention = interventionRepository.save(intervention);

            auditLogService.save("PLANIFICATION_AUTO", intervention.getId(), "INTERVENTION", null,
                    "Intervention périodique générée automatiquement — tunnel : " + tunnel.getLibelle()
                    + " — type : " + InterventionType.VISITE_SECURITE_ANNUELLE.getLibelle()
                    + " — date prévue : " + datePrevue.format(FMT)
                    + " — motif : visite obligatoire échéant le " + echeance.format(FMT));
            count++;
        }

        log.info("Planification automatique terminée: {} interventions générées", count);
        return count;
    }
}
