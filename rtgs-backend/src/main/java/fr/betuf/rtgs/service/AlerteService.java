package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.AlerteDTO;
import fr.betuf.rtgs.dto.TraiterAlerteRequest;
import fr.betuf.rtgs.entity.AuditLog;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.entity.enums.InterventionStatut;
import fr.betuf.rtgs.entity.enums.TunnelStatut;
import fr.betuf.rtgs.entity.enums.UserRole;
import fr.betuf.rtgs.dto.AlerteAssignmentDTO;
import fr.betuf.rtgs.entity.AlerteAssignment;
import fr.betuf.rtgs.entity.Intervention;
import fr.betuf.rtgs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlerteService {

    private final AuditLogRepository auditLogRepository;
    private final TunnelRepository tunnelRepository;
    private final InterventionRepository interventionRepository;
    private final AffectationRepository affectationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AlerteAssignmentRepository alerteAssignmentRepository;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<AlerteDTO> getAlertesActives(String niveau, Utilisateur user) {
        List<AuditLog> alertes;

        if (user != null && user.getRole() == UserRole.CHARGE_MISSION) {
            // CDM : alertes PREVENTIF/RETARD/INFO sur ses propres interventions
            alertes = auditLogRepository.findActiveAlertesInterventionByCdm(user.getId());
        } else {
            // ADMIN : alertes CRITIQUE uniquement
            alertes = auditLogRepository.findActiveCritiques();
        }

        Set<Long> traiteeIds = auditLogRepository.findByTypeActionOrderByDateHeureDesc("ALERTE_TRAITEE")
                .stream()
                .filter(a -> a.getIdObjet() != null)
                .map(AuditLog::getIdObjet)
                .collect(Collectors.toSet());

        return alertes.stream()
                .filter(a -> a.getIdObjet() == null || !traiteeIds.contains(a.getIdObjet()))
                .map(this::toAlertDTO)
                .collect(Collectors.toList());
    }

    /** CDM acquitte directement une alerte PREVENTIF/INFO sur l'une de ses interventions */
    public void marquerCdmTraitee(Long auditLogId, String commentaire, Utilisateur cdm) {
        AuditLog alert = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte non trouvée"));

        if (!"INTERVENTION".equals(alert.getTypeObjet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette action est réservée aux alertes portant sur une intervention");
        }

        Intervention intervention = interventionRepository.findById(alert.getIdObjet())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intervention non trouvée"));

        if (intervention.getCreateur() == null || !cdm.getId().equals(intervention.getCreateur().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous n'êtes pas le chargé de mission responsable de cette intervention");
        }

        String details = "Alerte acquittée par " + cdm.getNomComplet()
                + " — commentaire : " + (commentaire != null && !commentaire.isBlank() ? commentaire : "aucun");
        auditLogService.save("ALERTE_TRAITEE", alert.getIdObjet(), alert.getTypeObjet(), cdm, details);
    }

    @Scheduled(cron = "0 0 6 * * *")
    public List<AlerteDTO> calculerAlertes() {
        log.info("Calcul des alertes programmé en cours...");
        List<AlerteDTO> created = new ArrayList<>();

        LocalDate cutoff365 = LocalDate.now().minusDays(365);
        LocalDate seuil7j = LocalDate.now().plusDays(7);

        // CRITIQUE: tunnels > 1000m sans visite depuis > 365 jours
        // ET sans intervention de visite déjà planifiée ou en cours
        List<InterventionStatut> statutsActifs = List.of(
                InterventionStatut.PLANIFIEE, InterventionStatut.EN_COURS, InterventionStatut.BROUILLON);
        tunnelRepository.findByStatutAndLongueurGreaterThan(TunnelStatut.ACTIF, 1000.0)
                .forEach(tunnel -> {
                    boolean overdue = tunnel.getDateDerniereVisite() == null ||
                            tunnel.getDateDerniereVisite().isBefore(cutoff365);
                    boolean dejaEnCours = interventionRepository.existsByTunnelIdAndStatutIn(tunnel.getId(), statutsActifs);
                    if (overdue && !dejaEnCours && !auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_CRITIQUE", tunnel.getId())) {
                        long nbJours = tunnel.getDateDerniereVisite() == null ? 999 :
                                java.time.temporal.ChronoUnit.DAYS.between(tunnel.getDateDerniereVisite(), LocalDate.now());
                        String details = tunnel.getLibelle()
                                + " — " + nbJours + " jours sans visite réglementaire (limite : 365 jours)"
                                + " — dernière visite : "
                                + (tunnel.getDateDerniereVisite() != null ? tunnel.getDateDerniereVisite().format(FMT) : "jamais");
                        AuditLog auditEntry = auditLogService.save("ALERTE_CRITIQUE", tunnel.getId(), "TUNNEL", null, details);
                        created.add(toAlertDTO(auditEntry));
                        log.info("Alerte CRITIQUE créée: {}", details);
                    }
                });

        // PREVENTIF: interventions PLANIFIEE sans intervenant dans les 7 prochains jours
        interventionRepository.findByStatutAndDatePrevueBefore(InterventionStatut.PLANIFIEE, seuil7j)
                .forEach(intervention -> {
                    long count = affectationRepository.countByInterventionId(intervention.getId());
                    if (count == 0 && !auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_PREVENTIF", intervention.getId())) {
                        long nbJours = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), intervention.getDatePrevue());
                        String details = intervention.getReference()
                                + " planifiée le " + intervention.getDatePrevue().format(FMT)
                                + " sans intervenant affecté"
                                + " — J-" + Math.abs(nbJours) + " dépassé";
                        AuditLog auditEntry = auditLogService.save("ALERTE_PREVENTIF", intervention.getId(), "INTERVENTION", null, details);
                        created.add(toAlertDTO(auditEntry));
                    }
                });

        // PREVENTIF: interventions EN_COURS dont la date fin prévue est dépassée
        interventionRepository.findByStatutOrderByDatePrevueDesc(InterventionStatut.EN_COURS)
                .forEach(intervention -> {
                    if (intervention.getDateFinPrevue() != null
                            && intervention.getDateFinPrevue().isBefore(LocalDate.now())
                            && !auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_PREVENTIF_RETARD", intervention.getId())) {
                        long nbJours = java.time.temporal.ChronoUnit.DAYS.between(intervention.getDateFinPrevue(), LocalDate.now());
                        String details = intervention.getReference()
                                + " EN_COURS — date fin prévue dépassée de " + nbJours + " jour(s)"
                                + " — fin prévue le " + intervention.getDateFinPrevue().format(FMT);
                        AuditLog auditEntry = auditLogService.save("ALERTE_PREVENTIF_RETARD", intervention.getId(), "INTERVENTION", null, details);
                        created.add(toAlertDTO(auditEntry));
                    }
                });

        // INFO: interventions A_CLOTURER sans compte rendu
        interventionRepository.findByStatutOrderByDatePrevueDesc(InterventionStatut.A_CLOTURER)
                .forEach(intervention -> {
                    if ((intervention.getResume() == null || intervention.getResume().isBlank())
                            && !auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_INFO", intervention.getId())) {
                        long nbJours = intervention.getDatePrevue() != null ?
                                java.time.temporal.ChronoUnit.DAYS.between(intervention.getDatePrevue(), LocalDate.now()) : 0;
                        String details = intervention.getReference()
                                + " — compte rendu manquant pour clôture"
                                + " — en attente depuis " + nbJours + " jours";
                        AuditLog auditEntry = auditLogService.save("ALERTE_INFO", intervention.getId(), "INTERVENTION", null, details);
                        created.add(toAlertDTO(auditEntry));
                    }
                });

        // CONFLIT : ingénieurs affectés simultanément à deux interventions dont les dates se chevauchent
        List<Intervention> actives = interventionRepository.findByStatutIn(
                List.of(InterventionStatut.PLANIFIEE, InterventionStatut.EN_COURS,
                        InterventionStatut.SUSPENDUE, InterventionStatut.BROUILLON));

        for (int i = 0; i < actives.size(); i++) {
            for (int j = i + 1; j < actives.size(); j++) {
                Intervention int1 = actives.get(i);
                Intervention int2 = actives.get(j);

                if (!datesOverlap(int1, int2)) continue;

                Set<Long> ids1 = int1.getAffectations().stream()
                        .map(a -> a.getUtilisateur().getId()).collect(Collectors.toSet());
                Set<Long> ids2 = int2.getAffectations().stream()
                        .map(a -> a.getUtilisateur().getId()).collect(Collectors.toSet());
                ids1.retainAll(ids2);

                if (!ids1.isEmpty()
                        && !auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_CONFLIT", int1.getId())) {
                    String nomsEnConflits = int1.getAffectations().stream()
                            .filter(a -> ids1.contains(a.getUtilisateur().getId()))
                            .map(a -> a.getUtilisateur().getNomComplet())
                            .collect(Collectors.joining(", "));
                    String details = int1.getReference()
                            + " — conflit de planning avec " + int2.getReference()
                            + " — ingénieur(s) doublement affecté(s) : " + nomsEnConflits
                            + " — chevauchement : " + int1.getDatePrevue().format(FMT)
                            + " ↔ " + int2.getDatePrevue().format(FMT);
                    AuditLog entry = auditLogService.save("ALERTE_CONFLIT", int1.getId(), "INTERVENTION", null, details);
                    created.add(toAlertDTO(entry));
                    log.info("Alerte CONFLIT créée: {}", details);
                }
            }
        }

        log.info("Calcul des alertes terminé: {} nouvelles alertes", created.size());
        return created;
    }

    private boolean datesOverlap(Intervention i1, Intervention i2) {
        LocalDate start1 = i1.getDatePrevue();
        LocalDate end1 = i1.getDateFinPrevue() != null ? i1.getDateFinPrevue() : start1;
        LocalDate start2 = i2.getDatePrevue();
        LocalDate end2 = i2.getDateFinPrevue() != null ? i2.getDateFinPrevue() : start2;
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    public List<Map<String, Object>> getChargesMissionAvecCharge() {
        List<Utilisateur> cdms = utilisateurRepository.findByRole(UserRole.CHARGE_MISSION);
        long totalInterventionsActives = interventionRepository.findByStatutIn(
                List.of(InterventionStatut.PLANIFIEE, InterventionStatut.EN_COURS, InterventionStatut.A_CLOTURER, InterventionStatut.SUSPENDUE)
        ).size();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Utilisateur cdm : cdms) {
            long actives = interventionRepository.countActivesByCreateurId(cdm.getId());
            long aCloturer = interventionRepository.countACloturerByCreateurId(cdm.getId());
            long total = actives + aCloturer;

            int pourcentage = totalInterventionsActives > 0
                    ? (int) Math.round((double) total / totalInterventionsActives * 100)
                    : 0;

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", cdm.getId());
            info.put("nomComplet", cdm.getNomComplet());
            info.put("email", cdm.getEmail());
            info.put("interventionsActives", actives);
            info.put("interventionsACloturer", aCloturer);
            info.put("totalEnCharge", total);
            info.put("pourcentageCharge", pourcentage);
            result.add(info);
        }

        result.sort((a, b) -> Long.compare(
                (Long) a.get("totalEnCharge"),
                (Long) b.get("totalEnCharge")
        ));

        return result;
    }

    public AlerteDTO traiterAlerte(Long auditLogId, TraiterAlerteRequest request, Utilisateur utilisateur) {
        AuditLog original = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte non trouvée"));

        AlerteDTO dto = toAlertDTO(original);
        String commentaire = request.getCommentaire() != null && !request.getCommentaire().isBlank()
                ? request.getCommentaire() : "aucun";

        Utilisateur chargeMission;
        Intervention correctiveIntervention = null;

        boolean isNcCritique = "NC_CRITIQUE".equals(dto.getNiveau());

        if ("INTERVENTION".equals(original.getTypeObjet()) && !isNcCritique && original.getIdObjet() != null) {
            // Alertes PREVENTIF/CONFLIT/INFO sur interventions : CDM = créateur de l'intervention
            Intervention intervention = interventionRepository.findById(original.getIdObjet()).orElse(null);
            if (intervention != null && intervention.getCreateur() != null) {
                chargeMission = intervention.getCreateur();
                if (chargeMission.getRole() != UserRole.CHARGE_MISSION) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Le créateur de l'intervention n'est pas un chargé de mission");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Impossible de déterminer le chargé de mission pour cette intervention");
            }
        } else {
            // ALERTE_CRITIQUE (tunnel) ou ALERTE_NC_CRITIQUE : admin sélectionne le CDM
            if (request.getChargeMissionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un chargé de mission doit être désigné pour traiter l'alerte");
            }
            chargeMission = utilisateurRepository.findById(request.getChargeMissionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chargé de mission non trouvé"));
            if (chargeMission.getRole() != UserRole.CHARGE_MISSION) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "L'utilisateur désigné n'a pas le rôle CHARGE_MISSION");
            }

            // Pour NC_CRITIQUE : assigner la corrective au CDM désigné
            if (isNcCritique && original.getIdObjet() != null) {
                correctiveIntervention = interventionRepository.findById(original.getIdObjet()).orElse(null);
                if (correctiveIntervention != null) {
                    correctiveIntervention.setCreateur(chargeMission);
                    interventionRepository.save(correctiveIntervention);
                }
            }
        }

        String details = "Alerte " + dto.getNiveau()
                + " traitée — objet : " + dto.getObjetLibelle()
                + " — chargé de mission désigné : " + chargeMission.getNomComplet()
                + " — commentaire : " + commentaire
                + " — par : " + utilisateur.getNomComplet();

        auditLogService.save("ALERTE_TRAITEE", original.getIdObjet(), original.getTypeObjet(), utilisateur, details);
        auditLogService.save("DESIGNATION_CHARGE_MISSION", original.getIdObjet(), original.getTypeObjet(), chargeMission,
                "Désigné(e) par " + utilisateur.getNomComplet()
                + " pour traiter l'alerte " + dto.getNiveau()
                + " sur " + dto.getObjetLibelle());

        AlerteAssignment assignment = AlerteAssignment.builder()
                .alerteAuditLogId(auditLogId)
                .chargeMission(chargeMission)
                .assignePar(utilisateur)
                .statut("ASSIGNEE")
                .niveau(dto.getNiveau())
                .objetType(dto.getObjetType())
                .objetId(dto.getObjetId())
                .objetLibelle(dto.getObjetLibelle())
                .description(dto.getDescription())
                .commentaire(commentaire)
                .intervention(correctiveIntervention) // pré-lié pour NC_CRITIQUE
                .build();
        alerteAssignmentRepository.save(assignment);

        dto.setChargeMissionId(chargeMission.getId());
        dto.setChargeMissionNom(chargeMission.getNomComplet());
        dto.setStatut("TRAITEE");
        return dto;
    }

    public List<AlerteAssignmentDTO> getMesAlertes(Long chargeMissionId) {
        return alerteAssignmentRepository.findByChargeMissionIdOrderByCreatedAtDesc(chargeMissionId)
                .stream().map(this::toAssignmentDTO).collect(Collectors.toList());
    }

    public List<AlerteAssignmentDTO> getMesAlertesEnAttente(Long chargeMissionId) {
        return alerteAssignmentRepository.findByChargeMissionIdAndStatutOrderByCreatedAtDesc(chargeMissionId, "ASSIGNEE")
                .stream().map(this::toAssignmentDTO).collect(Collectors.toList());
    }

    @Transactional
    public AlerteAssignmentDTO lierIntervention(Long assignmentId, Long interventionId) {
        AlerteAssignment assignment = alerteAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment non trouvé"));

        Intervention intervention = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intervention non trouvée"));

        assignment.setIntervention(intervention);
        assignment.setStatut("TRAITEE");
        assignment.setTraiteAt(java.time.LocalDateTime.now());
        alerteAssignmentRepository.save(assignment);

        auditLogService.save("ALERTE_RESOLUE", assignment.getObjetId(), assignment.getObjetType(),
                assignment.getChargeMission(),
                "Alerte " + assignment.getNiveau() + " résolue par " + assignment.getChargeMission().getNomComplet()
                + " — intervention créée : " + intervention.getReference());

        return toAssignmentDTO(assignment);
    }

    public Map<String, Object> getPerformanceCdm(Long chargeMissionId) {
        long total = alerteAssignmentRepository.countByChargeMissionId(chargeMissionId);
        long enAttente = alerteAssignmentRepository.countByChargeMissionIdAndStatut(chargeMissionId, "ASSIGNEE");
        long traitees = alerteAssignmentRepository.countTraiteesParCdm(chargeMissionId);

        List<AlerteAssignment> resolved = alerteAssignmentRepository
                .findByChargeMissionIdAndTraiteAtIsNotNull(chargeMissionId);
        double avgHeures = 0;
        if (!resolved.isEmpty()) {
            avgHeures = resolved.stream()
                    .mapToLong(a -> java.time.Duration.between(a.getCreatedAt(), a.getTraiteAt()).toHours())
                    .average().orElse(0);
        }

        Map<String, Object> perf = new java.util.LinkedHashMap<>();
        perf.put("alertesTotales", total);
        perf.put("alertesEnAttente", enAttente);
        perf.put("alertesTraitees", traitees);
        perf.put("tempsTraitementMoyenHeures", Math.round(avgHeures));
        perf.put("tauxTraitement", total > 0 ? Math.round((double) traitees / total * 100) : 0);
        return perf;
    }

    private AlerteAssignmentDTO toAssignmentDTO(AlerteAssignment a) {
        return AlerteAssignmentDTO.builder()
                .id(a.getId())
                .alerteAuditLogId(a.getAlerteAuditLogId())
                .niveau(a.getNiveau())
                .objetType(a.getObjetType())
                .objetId(a.getObjetId())
                .objetLibelle(a.getObjetLibelle())
                .description(a.getDescription())
                .commentaire(a.getCommentaire())
                .statut(a.getStatut())
                .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
                .traiteAt(a.getTraiteAt() != null ? a.getTraiteAt().toString() : null)
                .chargeMissionId(a.getChargeMission().getId())
                .chargeMissionNom(a.getChargeMission().getNomComplet())
                .assigneParId(a.getAssignePar() != null ? a.getAssignePar().getId() : null)
                .assigneParNom(a.getAssignePar() != null ? a.getAssignePar().getNomComplet() : null)
                .interventionId(a.getIntervention() != null ? a.getIntervention().getId() : null)
                .interventionReference(a.getIntervention() != null ? a.getIntervention().getReference() : null)
                .build();
    }

    private AlerteDTO toAlertDTO(AuditLog log) {
        String niveau = "INFO";
        if (log.getTypeAction().contains("_")) {
            String[] parts = log.getTypeAction().split("_", 2);
            if (parts.length > 1) niveau = parts[1];
        }

        String objetLibelle = "";
        String description = log.getDetails() != null ? log.getDetails() : "";
        if (log.getDetails() != null && log.getDetails().contains(" — ")) {
            String[] parts = log.getDetails().split(" — ", 2);
            objetLibelle = parts[0];
            description = parts.length > 1 ? parts[1] : "";
        }

        return AlerteDTO.builder()
                .id(log.getId())
                .niveau(niveau)
                .objetType(log.getTypeObjet())
                .objetId(log.getIdObjet())
                .objetLibelle(objetLibelle)
                .description(description)
                .dateDetection(log.getDateHeure() != null ? log.getDateHeure().toString() : "")
                .statut("ACTIVE")
                .build();
    }
}
