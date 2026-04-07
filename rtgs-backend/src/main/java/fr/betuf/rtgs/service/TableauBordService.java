package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.ChargeIngenieurDTO;
import fr.betuf.rtgs.dto.KpiDTO;
import fr.betuf.rtgs.dto.UserPerformanceDTO;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.entity.Intervention;
import fr.betuf.rtgs.entity.enums.*;
import fr.betuf.rtgs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class TableauBordService {

    private final TunnelRepository tunnelRepository;
    private final InterventionRepository interventionRepository;
    private final AuditLogRepository auditLogRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AffectationRepository affectationRepository;
    private final AlerteAssignmentRepository alerteAssignmentRepository;

    public KpiDTO getKpi() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        // Alertes actives
        long critique = auditLogRepository.countActiveByTypeAction("ALERTE_CRITIQUE");
        long preventif = auditLogRepository.countActiveByTypeAction("ALERTE_PREVENTIF");
        long info = auditLogRepository.countActiveByTypeAction("ALERTE_INFO");

        long enRetard = interventionRepository.countEnRetard(today);
        long ceMois = interventionRepository.countByDatePrevueBetween(firstOfMonth, lastOfMonth);

        // Taux conformité
        List<?> activeBig = tunnelRepository.findByStatutAndLongueurGreaterThan(TunnelStatut.ACTIF, 1000.0);
        int totalBig = activeBig.size();
        LocalDate cutoff = today.minusDays(365);
        long conformes = tunnelRepository.findByStatutAndLongueurGreaterThan(TunnelStatut.ACTIF, 1000.0)
                .stream().filter(t -> t.getDateDerniereVisite() != null
                        && !t.getDateDerniereVisite().isBefore(cutoff)).count();
        long aPlanifier = tunnelRepository.findByStatutAndLongueurGreaterThan(TunnelStatut.ACTIF, 1000.0)
                .stream().filter(t -> t.getDateDerniereVisite() != null
                        && t.getDateDerniereVisite().isAfter(today.minusDays(365))
                        && t.getDateDerniereVisite().isBefore(today.minusDays(300))).count();
        long enRetardTunnels = totalBig - conformes;
        int taux = totalBig > 0 ? (int) Math.round((double) conformes / totalBig * 100) : 0;

        long tunnelsActifs = tunnelRepository.findByStatut(TunnelStatut.ACTIF).size();
        long totalTunnels = tunnelRepository.count();

        return KpiDTO.builder()
                .alertesCritiques((int) critique)
                .alertesPreventives((int) preventif)
                .alertesInfo((int) info)
                .interventionsEnRetard((int) enRetard)
                .interventionsCeMois((int) ceMois)
                .tauxConformite(taux)
                .tunnelsActifs((int) tunnelsActifs)
                .tunnelsConformes((int) conformes)
                .tunnelsAPlanifier((int) aPlanifier)
                .tunnelsEnRetard((int) enRetardTunnels)
                .totalTunnels((int) totalTunnels)
                .build();
    }

    public Map<String, Object> getKpiCdm(Long cdmId) {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        long alertesEnAttente = alerteAssignmentRepository.countByChargeMissionIdAndStatut(cdmId, "ASSIGNEE");
        long alertesTraitees = alerteAssignmentRepository.countTraiteesParCdm(cdmId);
        long alertesTotal = alerteAssignmentRepository.countByChargeMissionId(cdmId);

        long interventionsCeMois = interventionRepository.countByCreateurIdAndDatePrevueBetween(cdmId, firstOfMonth, lastOfMonth);
        long interventionsEnRetard = interventionRepository.countEnRetardByCreateurId(cdmId, today);
        long interventionsCloturees = interventionRepository.countClotureesByCreateurId(cdmId);
        long interventionsActives = interventionRepository.countActivesByCreateurId(cdmId);
        long interventionsACloturer = interventionRepository.countACloturerByCreateurId(cdmId);
        long interventionsTotal = interventionRepository.countByCreateurId(cdmId);

        int tauxCloture = interventionsTotal > 0
                ? (int) Math.round((double) interventionsCloturees / interventionsTotal * 100) : 0;
        int tauxTraitementAlertes = alertesTotal > 0
                ? (int) Math.round((double) alertesTraitees / alertesTotal * 100) : 0;

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("alertesEnAttente", alertesEnAttente);
        kpi.put("alertesTraitees", alertesTraitees);
        kpi.put("alertesTotal", alertesTotal);
        kpi.put("tauxTraitementAlertes", tauxTraitementAlertes);
        kpi.put("interventionsCeMois", interventionsCeMois);
        kpi.put("interventionsEnRetard", interventionsEnRetard);
        kpi.put("interventionsActives", interventionsActives);
        kpi.put("interventionsACloturer", interventionsACloturer);
        kpi.put("interventionsCloturees", interventionsCloturees);
        kpi.put("interventionsTotal", interventionsTotal);
        kpi.put("tauxCloture", tauxCloture);
        return kpi;
    }

    public Map<String, Object> getKpiIngenieur(Long ingId) {
        LocalDate today = LocalDate.now();

        long missionsChef = affectationRepository.countChefMissionActives(ingId);
        long missionsIntervenant = affectationRepository.countIntervenantActives(ingId);
        long missionsTotal = affectationRepository.countByUtilisateurId(ingId);
        long missionsCloturees = affectationRepository.countClotureesMissionsForUser(ingId);
        long missionsEnRetard = affectationRepository.countEnRetardMissionsForUser(ingId, today);
        long missionsActives = affectationRepository.countActiveMissionsForUser(ingId);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("missionsChefActives", missionsChef);
        kpi.put("missionsIntervenantActives", missionsIntervenant);
        kpi.put("missionsActives", missionsActives);
        kpi.put("missionsEnRetard", missionsEnRetard);
        kpi.put("missionsCloturees", missionsCloturees);
        kpi.put("missionsTotal", missionsTotal);
        return kpi;
    }

    public List<UserPerformanceDTO> getPerformancesAll() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        List<Utilisateur> users = utilisateurRepository.findAll();
        return users.stream()
                .filter(u -> u.getRole() == UserRole.INGENIEUR || u.getRole() == UserRole.CHARGE_MISSION)
                .map(u -> {
                    UserPerformanceDTO.UserPerformanceDTOBuilder b = UserPerformanceDTO.builder()
                            .id(u.getId())
                            .nomComplet(u.getNomComplet())
                            .email(u.getEmail())
                            .role(u.getRole().name())
                            .statut(u.getStatut().name())
                            .pole(u.getPole());

                    if (u.getRole() == UserRole.INGENIEUR) {
                        int actives   = (int) affectationRepository.countActiveMissionsForUser(u.getId());
                        int cloturees = (int) affectationRepository.countClotureesMissionsForUser(u.getId());
                        int enRetard  = (int) affectationRepository.countEnRetardMissionsForUser(u.getId(), today);
                        int total     = (int) affectationRepository.countByUtilisateurId(u.getId());
                        int chef      = (int) affectationRepository.countChefMissionActives(u.getId());
                        int intervenant = (int) affectationRepository.countIntervenantActives(u.getId());
                        // Seuil max raisonnable : 5 missions actives = 100%
                        int taux = Math.min((int) Math.round((double) actives / 5 * 100), 100);
                        String niveau = actives >= 5 ? "ELEVE" : actives >= 3 ? "MOYEN" : "OK";
                        b.missionsActives(actives).missionsCloturees(cloturees)
                         .missionsEnRetard(enRetard).missionsTotal(total)
                         .missionsChefActives(chef).missionsIntervenantActives(intervenant)
                         .tauxOccupation(taux).niveauCharge(niveau);
                    } else {
                        int actives    = (int) interventionRepository.countActivesByCreateurId(u.getId());
                        int cloturees  = (int) interventionRepository.countClotureesByCreateurId(u.getId());
                        int enRetard   = (int) interventionRepository.countEnRetardByCreateurId(u.getId(), today);
                        int aCloturer  = (int) interventionRepository.countACloturerByCreateurId(u.getId());
                        int total      = (int) interventionRepository.countByCreateurId(u.getId());
                        int alertesAtt = (int) alerteAssignmentRepository.countByChargeMissionIdAndStatut(u.getId(), "ASSIGNEE");
                        int alertesTrt = (int) alerteAssignmentRepository.countTraiteesParCdm(u.getId());
                        int alertesTot = (int) alerteAssignmentRepository.countByChargeMissionId(u.getId());
                        int tauxCloture = total > 0 ? (int) Math.round((double) cloturees / total * 100) : 0;
                        int tauxAlertes = alertesTot > 0 ? (int) Math.round((double) alertesTrt / alertesTot * 100) : 0;
                        int taux = total > 0 ? Math.min((int) Math.round((double) actives / 10 * 100), 100) : 0;
                        b.missionsActives(actives).missionsCloturees(cloturees)
                         .missionsEnRetard(enRetard).missionsTotal(total)
                         .interventionsACloturer(aCloturer)
                         .alertesEnAttente(alertesAtt).alertesTraitees(alertesTrt)
                         .alertesTotal(alertesTot).tauxTraitementAlertes(tauxAlertes)
                         .tauxCloture(tauxCloture).tauxOccupation(taux);
                    }
                    return b.build();
                })
                .sorted(Comparator.comparing(UserPerformanceDTO::getRole)
                        .thenComparing(Comparator.comparingInt(UserPerformanceDTO::getMissionsActives).reversed()))
                .collect(Collectors.toList());
    }

    public List<ChargeIngenieurDTO> getChargeIngenieurs() {
        return utilisateurRepository.findByRole(UserRole.INGENIEUR)
                .stream().map(u -> {
                    int nb = (int) affectationRepository.countActiveMissionsForUser(u.getId());
                    String niveau = nb >= 5 ? "ELEVE" : nb >= 3 ? "MOYEN" : "OK";
                    return ChargeIngenieurDTO.builder()
                            .id(u.getId())
                            .nomComplet(u.getNomComplet())
                            .pole(u.getPole())
                            .competences(u.getCompetences())
                            .nbMissionsActives(nb)
                            .niveau(niveau)
                            .statut(u.getStatut().name())
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getNbMissionsActives(), a.getNbMissionsActives()))
                .collect(Collectors.toList());
    }
}
