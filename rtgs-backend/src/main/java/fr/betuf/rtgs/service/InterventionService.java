package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.*;
import fr.betuf.rtgs.entity.*;
import fr.betuf.rtgs.entity.enums.*;
import fr.betuf.rtgs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final TunnelRepository tunnelRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AffectationRepository affectationRepository;
    private final NonConformiteRepository nonConformiteRepository;
    private final AuditLogService auditLogService;
    private final TunnelService tunnelService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<InterventionDTO> getAll(String statut, String type, Long tunnelId, String dateFrom, String dateTo, Utilisateur currentUser) {
        InterventionStatut statutEnum = statut != null && !statut.isBlank()
                ? InterventionStatut.valueOf(statut) : null;
        InterventionType typeEnum = type != null && !type.isBlank()
                ? InterventionType.valueOf(type) : null;
        LocalDate from = dateFrom != null && !dateFrom.isBlank() ? LocalDate.parse(dateFrom) : null;
        LocalDate to = dateTo != null && !dateTo.isBlank() ? LocalDate.parse(dateTo) : null;

        List<Intervention> list;
        if (currentUser != null && currentUser.getRole() == UserRole.INGENIEUR) {
            list = interventionRepository.findByAffectationUserId(currentUser.getId());
        } else if (currentUser != null && currentUser.getRole() == UserRole.CHARGE_MISSION) {
            list = interventionRepository.findByCreateurIdOrderByDatePrevueDesc(currentUser.getId());
        } else {
            list = interventionRepository.findAllByOrderByDatePrevueDesc();
        }

        return list.stream()
                .filter(i -> statutEnum == null || i.getStatut() == statutEnum)
                .filter(i -> typeEnum == null || i.getType() == typeEnum)
                .filter(i -> tunnelId == null || (i.getTunnel() != null && tunnelId.equals(i.getTunnel().getId())))
                .filter(i -> from == null || (i.getDatePrevue() != null && !i.getDatePrevue().isBefore(from)))
                .filter(i -> to == null || (i.getDatePrevue() != null && !i.getDatePrevue().isAfter(to)))
                .map(i -> toDTO(i, currentUser))
                .collect(Collectors.toList());
    }

    public InterventionDTO getById(Long id) {
        return toDTO(findById(id));
    }

    private Intervention findById(Long id) {
        return interventionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intervention non trouvée"));
    }

    @Transactional
    public InterventionDTO create(InterventionDTO dto, Utilisateur utilisateur) {
        LocalDate datePrevue = LocalDate.parse(dto.getDatePrevue());
        if (datePrevue.isBefore(LocalDate.now().plusDays(2))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date prévue doit être au minimum J+2 (règle R8)");
        }

        Tunnel tunnel = tunnelRepository.findById(dto.getTunnel().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tunnel non trouvé"));

        Intervention intervention = Intervention.builder()
                .tunnel(tunnel)
                .type(InterventionType.valueOf(dto.getType()))
                .statut(InterventionStatut.BROUILLON)
                .datePrevue(datePrevue)
                .dateFinPrevue(dto.getDateFinPrevue() != null && !dto.getDateFinPrevue().isBlank()
                        ? LocalDate.parse(dto.getDateFinPrevue()) : null)
                .competencesRequises(dto.getCompetencesRequises())
                .description(dto.getDescription())
                .createur(utilisateur)
                .build();

        intervention = interventionRepository.save(intervention);

        auditLogService.save("CREATION", intervention.getId(), "INTERVENTION", utilisateur,
                intervention.getReference() + " créée — tunnel : " + tunnel.getLibelle()
                + " — type : " + intervention.getType().getLibelle()
                + " — par : " + utilisateur.getNomComplet());

        return toDTO(intervention);
    }

    @Transactional
    public InterventionDTO update(Long id, InterventionDTO dto, Utilisateur utilisateur) {
        Intervention intervention = findById(id);

        boolean isCreateur = utilisateur != null && intervention.getCreateur() != null
                && utilisateur.getId().equals(intervention.getCreateur().getId());

        boolean canEdit = intervention.getStatut() == InterventionStatut.BROUILLON
                || (isCreateur && intervention.getStatut() == InterventionStatut.PLANIFIEE);

        if (!canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Modification impossible: statut " + intervention.getStatut()
                    + (isCreateur ? "" : " — vous n'êtes pas le créateur de cette intervention"));
        }

        if (dto.getDatePrevue() != null) intervention.setDatePrevue(LocalDate.parse(dto.getDatePrevue()));
        if (dto.getDateFinPrevue() != null && !dto.getDateFinPrevue().isBlank())
            intervention.setDateFinPrevue(LocalDate.parse(dto.getDateFinPrevue()));
        if (dto.getDescription() != null) intervention.setDescription(dto.getDescription());
        if (dto.getCompetencesRequises() != null) intervention.setCompetencesRequises(dto.getCompetencesRequises());

        intervention = interventionRepository.save(intervention);

        auditLogService.save("MODIFICATION", id, "INTERVENTION", utilisateur,
                intervention.getReference() + " modifiée par " + utilisateur.getNomComplet());

        return toDTO(intervention);
    }

    @Transactional
    public InterventionDTO changeStatut(Long id, String newStatut, Utilisateur utilisateur) {
        Intervention intervention = findById(id);
        InterventionStatut ancien = intervention.getStatut();
        UserRole role = utilisateur != null ? utilisateur.getRole() : null;

        boolean isChef = intervention.getAffectations() != null
                && utilisateur != null
                && intervention.getAffectations().stream()
                .anyMatch(a -> a.getUtilisateur().getId().equals(utilisateur.getId())
                        && a.getRole().toLowerCase().contains("chef"));

        switch (newStatut) {
            case "EN_COURS":
                if (ancien != InterventionStatut.PLANIFIEE && ancien != InterventionStatut.SUSPENDUE) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Seule une intervention PLANIFIEE ou SUSPENDUE peut être passée en EN_COURS");
                }
                if (role == UserRole.INGENIEUR && !isChef) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Seul le chef de mission peut démarrer/reprendre l'intervention");
                }
                break;

            case "SUSPENDUE":
                if (ancien != InterventionStatut.EN_COURS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Seule une intervention EN_COURS peut être suspendue");
                }
                if (role == UserRole.INGENIEUR && !isChef) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Seul le chef de mission peut suspendre l'intervention");
                }
                if (role != UserRole.INGENIEUR && role != UserRole.CHARGE_MISSION && role != UserRole.ADMIN) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle non autorisé");
                }
                break;

            case "ANNULEE":
                if (ancien == InterventionStatut.CLOTUREE || ancien == InterventionStatut.ANNULEE) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Impossible d'annuler une intervention déjà " + ancien);
                }
                if (ancien == InterventionStatut.EN_COURS || ancien == InterventionStatut.A_CLOTURER) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Impossible d'annuler une intervention " + ancien + ". Suspendez-la d'abord si nécessaire.");
                }
                if (role != UserRole.CHARGE_MISSION && role != UserRole.ADMIN) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Seul un chargé de mission ou un admin peut annuler une intervention");
                }
                break;

            default:
                if (role == UserRole.INGENIEUR) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Transition non autorisée pour un ingénieur");
                }
                break;
        }

        intervention.setStatut(InterventionStatut.valueOf(newStatut));
        intervention = interventionRepository.save(intervention);

        auditLogService.save("CHANGEMENT_STATUT", id, "INTERVENTION", utilisateur,
                intervention.getReference() + " — statut changé : "
                + ancien + " → " + newStatut
                + " — par : " + utilisateur.getNomComplet());

        if (ancien == InterventionStatut.SUSPENDUE
                && ("EN_COURS".equals(newStatut) || "PLANIFIEE".equals(newStatut))) {
            auditLogService.save("ALERTE_TRAITEE", id, "INTERVENTION", utilisateur,
                    intervention.getReference() + " — reprise après suspension"
                    + " — par : " + utilisateur.getNomComplet());
        }

        return toDTO(intervention);
    }

    @Transactional
    public InterventionDTO soumettreRapport(Long id, String resume, String recommandations, Utilisateur utilisateur) {
        Intervention intervention = findById(id);

        if (intervention.getStatut() != InterventionStatut.EN_COURS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le rapport ne peut être soumis que sur une intervention EN_COURS");
        }

        boolean isChef = intervention.getAffectations() != null
                && intervention.getAffectations().stream()
                .anyMatch(a -> a.getUtilisateur().getId().equals(utilisateur.getId())
                        && a.getRole().toLowerCase().contains("chef"));
        if (!isChef) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le chef de mission peut soumettre un rapport");
        }

        intervention.setResume(resume);
        intervention.setRecommandations(recommandations);
        intervention.setStatut(InterventionStatut.valueOf("A_CLOTURER"));
        intervention = interventionRepository.save(intervention);

        auditLogService.save("RAPPORT_SOUMIS", id, "INTERVENTION", utilisateur,
                intervention.getReference() + " — rapport soumis par " + utilisateur.getNomComplet()
                + " — en attente de clôture par le chargé de mission");

        auditLogService.save("CHANGEMENT_STATUT", id, "INTERVENTION", utilisateur,
                intervention.getReference() + " — statut changé : EN_COURS → A_CLOTURER"
                + " — par : " + utilisateur.getNomComplet());

        return toDTO(intervention);
    }

    public List<UtilisateurDispoDTO> getIntervenantsDisponibles(Long interventionId) {
        Intervention intervention = findById(interventionId);
        List<Utilisateur> ingenieurs = utilisateurRepository.findByRole(UserRole.INGENIEUR);

        Set<String> requiredComps = intervention.getCompetencesRequises() != null
                ? Set.of(intervention.getCompetencesRequises().split(","))
                : new HashSet<>();

        return ingenieurs.stream()
                .filter(u -> matchesAnyCompetence(u, requiredComps))
                .map(u -> computeDispo(u, intervention))
                .sorted(Comparator.comparing(d -> {
                    return switch (d.getDisponibilite()) {
                        case "DISPONIBLE" -> 0;
                        case "CONFLIT" -> 1;
                        default -> 2;
                    };
                }))
                .collect(Collectors.toList());
    }

    private boolean matchesAnyCompetence(Utilisateur u, Set<String> required) {
        if (required.isEmpty()) return true;
        if (u.getCompetences() == null) return false;
        Set<String> userComps = Set.of(u.getCompetences().split(","));
        return required.stream().anyMatch(r -> userComps.contains(r.trim()));
    }

    private UtilisateurDispoDTO computeDispo(Utilisateur u, Intervention intervention) {
        String disponibilite;
        if (u.getStatut() == UserStatut.INDISPONIBLE) {
            disponibilite = "INDISPONIBLE";
        } else {
            LocalDate debut = intervention.getDatePrevue();
            LocalDate fin = intervention.getDateFinPrevue() != null
                    ? intervention.getDateFinPrevue() : debut.plusDays(1);
            List<Affectation> conflicts = affectationRepository.findConflictsForUser(u.getId(), debut, fin);
            conflicts = conflicts.stream()
                    .filter(a -> !a.getIntervention().getId().equals(intervention.getId()))
                    .collect(Collectors.toList());
            disponibilite = conflicts.isEmpty() ? "DISPONIBLE" : "CONFLIT";
        }

        int nbMissions = (int) affectationRepository.countActiveMissionsForUser(u.getId());
        List<String> compsList = u.getCompetences() != null
                ? List.of(u.getCompetences().split(",")) : List.of();

        return UtilisateurDispoDTO.builder()
                .id(u.getId()).nomComplet(u.getNomComplet()).email(u.getEmail())
                .role(u.getRole().name()).statut(u.getStatut().name())
                .competences(u.getCompetences()).competencesList(compsList)
                .pole(u.getPole()).disponibilite(disponibilite)
                .nbMissionsActives(nbMissions).build();
    }

    public ConflitPlanningDTO detecterConflits(Long interventionId, Long utilisateurId) {
        Intervention intervention = findById(interventionId);
        utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        LocalDate debut = intervention.getDatePrevue();
        LocalDate fin = intervention.getDateFinPrevue() != null
                ? intervention.getDateFinPrevue() : debut.plusDays(1);

        List<Affectation> conflicts = affectationRepository.findConflictsForUser(utilisateurId, debut, fin)
                .stream().filter(a -> !a.getIntervention().getId().equals(interventionId))
                .collect(Collectors.toList());

        if (conflicts.isEmpty()) {
            return ConflitPlanningDTO.builder().aConflit(false).build();
        }

        Intervention conflicting = conflicts.get(0).getIntervention();
        LocalDate optionDecaler = conflicting.getDateFinPrevue() != null
                ? conflicting.getDateFinPrevue().plusDays(1)
                : conflicting.getDatePrevue().plusDays(1);

        // Alternatives: INGENIEUR with same competences, available, not same user
        List<UtilisateurDispoDTO> alternatives = getIntervenantsDisponibles(interventionId)
                .stream()
                .filter(u -> u.getDisponibilite().equals("DISPONIBLE") && !u.getId().equals(utilisateurId))
                .limit(3)
                .collect(Collectors.toList());

        return ConflitPlanningDTO.builder()
                .aConflit(true)
                .interventionEnConflit(toDTO(conflicting))
                .optionDecaler(optionDecaler.toString())
                .alternatives(alternatives)
                .build();
    }

    @Transactional
    public InterventionDTO affecter(Long interventionId, List<AffectationRequestDTO> dtos, Utilisateur cdm) {
        Intervention intervention = findById(interventionId);

        // Seul un ADMIN ou CHARGE_MISSION peut affecter
        if (cdm == null || (cdm.getRole() != UserRole.ADMIN && cdm.getRole() != UserRole.CHARGE_MISSION)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul un administrateur ou un chargé de mission peut affecter une équipe");
        }

        // R1: max 5 intervenants
        if (dtos.size() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum 5 intervenants par intervention (règle R1)");
        }

        // Validation: un chef de mission est obligatoire
        long chefsCount = dtos.stream()
                .filter(d -> "Chef de mission".equalsIgnoreCase(d.getRole()))
                .count();
        if (chefsCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un chef de mission doit être désigné (règle métier)");
        }
        if (chefsCount > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une seule personne peut être chef de mission");
        }

        // Validate users
        List<Utilisateur> users = new ArrayList<>();
        for (AffectationRequestDTO dto : dtos) {
            Utilisateur u = utilisateurRepository.findById(dto.getUtilisateurId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));
            // R2: no INDISPONIBLE
            if (u.getStatut() == UserStatut.INDISPONIBLE && !dto.isForceConflict()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Impossible d'affecter un utilisateur indisponible (règle R2): " + u.getNomComplet());
            }
            users.add(u);
        }

        // R3: competences coverage
        if (intervention.getCompetencesRequises() != null) {
            Set<String> required = new HashSet<>(Arrays.asList(intervention.getCompetencesRequises().split(",")));
            Set<String> covered = new HashSet<>();
            for (Utilisateur u : users) {
                if (u.getCompetences() != null) {
                    covered.addAll(Arrays.asList(u.getCompetences().split(",")));
                }
            }
            Set<String> missing = required.stream()
                    .filter(r -> covered.stream().noneMatch(c -> c.trim().equals(r.trim())))
                    .collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Compétences non couvertes: " + String.join(", ", missing) + " (règle R3)");
            }
        }

        // Delete existing affectations
        affectationRepository.deleteByInterventionId(interventionId);

        // Create new affectations
        List<String> equipeNames = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            AffectationRequestDTO dto = dtos.get(i);
            Utilisateur u = users.get(i);

            Affectation aff = Affectation.builder()
                    .intervention(intervention)
                    .utilisateur(u)
                    .role(dto.getRole() != null ? dto.getRole() : (i == 0 ? "Chef de mission" : "Intervenant"))
                    .motif(dto.getMotif())
                    .remplaceLId(dto.getRemplaceLId())
                    .build();
            affectationRepository.save(aff);
            equipeNames.add(u.getNomComplet());

            // AFFECTATION audit
            auditLogService.save("AFFECTATION", interventionId, "INTERVENTION", cdm,
                    u.getNomComplet() + " affecté(e) à " + intervention.getReference()
                    + " — rôle : " + aff.getRole()
                    + " — par : " + cdm.getNomComplet());

            // REMPLACEMENT audit
            if (dto.getRemplaceLId() != null) {
                Utilisateur ancien = utilisateurRepository.findById(dto.getRemplaceLId()).orElse(null);
                if (ancien != null) {
                    auditLogService.save("REMPLACEMENT", interventionId, "INTERVENTION", cdm,
                            ancien.getNomComplet() + " remplacé(e) par " + u.getNomComplet()
                            + " sur " + intervention.getReference()
                            + " — motif : " + (dto.getMotif() != null ? dto.getMotif() : "non précisé")
                            + " — décision de : " + cdm.getNomComplet());
                }
            }

            // AFFECTATION_FORCEE audit
            if (dto.isForceConflict()) {
                ConflitPlanningDTO conflit = detecterConflits(interventionId, u.getId());
                String conflictRef = conflit.getInterventionEnConflit() != null
                        ? conflit.getInterventionEnConflit().getReference() : "inconnue";
                auditLogService.save("AFFECTATION_FORCEE", interventionId, "INTERVENTION", cdm,
                        u.getNomComplet() + " affecté(e) malgré conflit de planning sur "
                        + intervention.getReference()
                        + " — conflit avec : " + conflictRef
                        + " — accepté par : " + cdm.getNomComplet());
            }
        }

        // Change statut to PLANIFIEE
        InterventionStatut ancienStatut = intervention.getStatut();
        intervention.setStatut(InterventionStatut.PLANIFIEE);
        interventionRepository.save(intervention);

        auditLogService.save("PLANIFICATION", interventionId, "INTERVENTION", cdm,
                intervention.getReference() + " planifiée — équipe : "
                + String.join(", ", equipeNames)
                + " — par : " + cdm.getNomComplet());

        auditLogService.save("CHANGEMENT_STATUT", interventionId, "INTERVENTION", cdm,
                intervention.getReference() + " — statut changé : "
                + ancienStatut + " → PLANIFIEE"
                + " — par : " + cdm.getNomComplet());

        if (ancienStatut == InterventionStatut.SUSPENDUE) {
            auditLogService.save("ALERTE_TRAITEE", interventionId, "INTERVENTION", cdm,
                    intervention.getReference() + " — reprise après réaffectation d'équipe"
                    + " — par : " + cdm.getNomComplet());
        }

        return toDTO(findById(interventionId));
    }

    @Transactional
    public CloturerResponse cloturer(Long id, CloturerRequest request, Utilisateur utilisateur) {
        Intervention intervention = findById(id);

        if (intervention.getStatut() != InterventionStatut.EN_COURS
                && intervention.getStatut() != InterventionStatut.A_CLOTURER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seule une intervention EN_COURS ou A_CLOTURER peut être clôturée");
        }

        if (request.getDateRealisation() != null) {
            intervention.setDateRealisation(LocalDate.parse(request.getDateRealisation()));
        }
        if (request.getResume() != null) {
            intervention.setResume(request.getResume());
        }
        if (request.getRecommandations() != null) {
            intervention.setRecommandations(request.getRecommandations());
        }

        int nbNc = 0;
        int nbCritiques = 0;
        String referenceCorrectif = null;

        if (request.getNonConformites() != null) {
            for (NonConformiteDTO ncDto : request.getNonConformites()) {
                NCGravite gravite = NCGravite.valueOf(ncDto.getGravite());
                NonConformite nc = NonConformite.builder()
                        .intervention(intervention)
                        .declarant(utilisateur)
                        .gravite(gravite)
                        .description(ncDto.getDescription())
                        .statut(NCStatut.DECLAREE)
                        .delaiCorrectionJours(ncDto.getDelaiCorrectionJours())
                        .build();
                nonConformiteRepository.save(nc);
                nbNc++;
                if (gravite == NCGravite.CRITIQUE) nbCritiques++;

                String descTruncated = ncDto.getDescription().length() > 100
                        ? ncDto.getDescription().substring(0, 100) + "..."
                        : ncDto.getDescription();
                auditLogService.save("CREATION_NC", intervention.getId(), "INTERVENTION", utilisateur,
                        "Non-conformité " + gravite + " déclarée sur " + intervention.getReference()
                        + " — description : " + descTruncated
                        + " — délai correction : " + ncDto.getDelaiCorrectionJours() + " jours"
                        + " — par : " + utilisateur.getNomComplet());

                // R5: CRITIQUE -> create corrective intervention
                if (gravite == NCGravite.CRITIQUE) {
                    Intervention corrective = Intervention.builder()
                            .tunnel(intervention.getTunnel())
                            .type(InterventionType.INTERVENTION_CORRECTIVE)
                            .statut(InterventionStatut.BROUILLON)
                            .datePrevue(LocalDate.now().plusDays(30))
                            .competencesRequises(intervention.getCompetencesRequises())
                            .description("Intervention corrective suite à NC critique sur "
                                    + intervention.getReference())
                            .build();
                    corrective = interventionRepository.save(corrective);
                    referenceCorrectif = corrective.getReference();

                    auditLogService.save("CREATION_CORRECTIVE", corrective.getId(), "INTERVENTION", null,
                            "Intervention corrective " + corrective.getReference()
                            + " créée automatiquement suite à NC critique sur " + intervention.getReference()
                            + " — tunnel : " + intervention.getTunnel().getLibelle());
                }
            }
        }

        InterventionStatut ancienStatut = intervention.getStatut();
        intervention.setStatut(InterventionStatut.CLOTUREE);
        interventionRepository.save(intervention);

        // Update tunnel last visit
        if (intervention.getDateRealisation() != null) {
            Tunnel tunnel = intervention.getTunnel();
            tunnel.setDateDerniereVisite(intervention.getDateRealisation());
            tunnelRepository.save(tunnel);
        }

        String dateRealStr = intervention.getDateRealisation() != null
                ? intervention.getDateRealisation().format(FMT) : "non renseignée";
        String details = intervention.getReference() + " clôturée — date réalisation : " + dateRealStr
                + " — " + nbNc + " non-conformité(s)"
                + (nbCritiques > 0 ? " dont " + nbCritiques + " critique(s)" : "")
                + " — par : " + utilisateur.getNomComplet();
        auditLogService.save("CLOTURE", id, "INTERVENTION", utilisateur, details);
        auditLogService.save("CHANGEMENT_STATUT", id, "INTERVENTION", utilisateur,
                intervention.getReference() + " — statut changé : " + ancienStatut + " → CLOTUREE"
                + " — par : " + utilisateur.getNomComplet());

        return CloturerResponse.builder()
                .intervention(toDTO(findById(id)))
                .referenceCorrectif(referenceCorrectif)
                .build();
    }

    private InterventionDTO toDTO(Intervention i, Utilisateur currentUser) {
        InterventionDTO dto = toDTO(i);
        if (currentUser != null && i.getAffectations() != null) {
            i.getAffectations().stream()
                    .filter(a -> a.getUtilisateur().getId().equals(currentUser.getId()))
                    .findFirst()
                    .ifPresent(a -> dto.setMonRole(a.getRole()));
        }
        return dto;
    }

    public InterventionDTO toDTO(Intervention i) {
        List<AffectationResponseDTO> affs = i.getAffectations() != null
                ? i.getAffectations().stream().map(this::toAffDTO).collect(Collectors.toList())
                : new ArrayList<>();
        List<NonConformiteDTO> ncs = i.getNonConformites() != null
                ? i.getNonConformites().stream().map(this::toNcDTO).collect(Collectors.toList())
                : new ArrayList<>();

        return InterventionDTO.builder()
                .id(i.getId()).reference(i.getReference())
                .tunnel(i.getTunnel() != null ? tunnelService.toDTO(i.getTunnel()) : null)
                .type(i.getType() != null ? i.getType().name() : null)
                .typeLibelle(i.getType() != null ? i.getType().getLibelle() : null)
                .statut(i.getStatut() != null ? i.getStatut().name() : null)
                .datePrevue(i.getDatePrevue() != null ? i.getDatePrevue().toString() : null)
                .dateFinPrevue(i.getDateFinPrevue() != null ? i.getDateFinPrevue().toString() : null)
                .dateRealisation(i.getDateRealisation() != null ? i.getDateRealisation().toString() : null)
                .competencesRequises(i.getCompetencesRequises())
                .description(i.getDescription()).resume(i.getResume()).recommandations(i.getRecommandations())
                .affectations(affs).nonConformites(ncs)
                .createdAt(i.getCreatedAt() != null ? i.getCreatedAt().toString() : null)
                .createurId(i.getCreateur() != null ? i.getCreateur().getId() : null)
                .createurNom(i.getCreateur() != null ? i.getCreateur().getNomComplet() : null)
                .build();
    }

    private AffectationResponseDTO toAffDTO(Affectation a) {
        return AffectationResponseDTO.builder()
                .id(a.getId())
                .utilisateurId(a.getUtilisateur().getId())
                .nomComplet(a.getUtilisateur().getNomComplet())
                .role(a.getRole())
                .pole(a.getUtilisateur().getPole())
                .competences(a.getUtilisateur().getCompetences())
                .build();
    }

    private NonConformiteDTO toNcDTO(NonConformite nc) {
        return NonConformiteDTO.builder()
                .id(nc.getId())
                .gravite(nc.getGravite().name())
                .description(nc.getDescription())
                .statut(nc.getStatut().name())
                .delaiCorrectionJours(nc.getDelaiCorrectionJours())
                .declarantId(nc.getDeclarant() != null ? nc.getDeclarant().getId() : null)
                .dateDeclaration(nc.getDateDeclaration() != null ? nc.getDateDeclaration().toString() : null)
                .build();
    }
}
