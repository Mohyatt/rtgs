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
    private final AlerteAssignmentRepository alerteAssignmentRepository;
    private final AuditLogRepository auditLogRepository;
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

        LocalDate dateFinPrevue = (dto.getDateFinPrevue() != null && !dto.getDateFinPrevue().isBlank())
                ? LocalDate.parse(dto.getDateFinPrevue()) : null;
        if (dateFinPrevue != null && !dateFinPrevue.isAfter(datePrevue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de fin prévue doit être postérieure à la date de début");
        }

        Tunnel tunnel = tunnelRepository.findById(dto.getTunnel().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tunnel non trouvé"));

        Intervention intervention = Intervention.builder()
                .tunnel(tunnel)
                .type(InterventionType.valueOf(dto.getType()))
                .statut(InterventionStatut.BROUILLON)
                .datePrevue(datePrevue)
                .dateFinPrevue(dateFinPrevue)
                .competencesRequises(dto.getCompetencesRequises())
                .description(dto.getDescription())
                .createur(utilisateur)
                .build();

        intervention = interventionRepository.save(intervention);

        auditLogService.save("CREATION", intervention.getId(), "INTERVENTION", utilisateur,
                intervention.getReference() + " créée — tunnel : " + tunnel.getLibelle()
                + " — type : " + intervention.getType().getLibelle()
                + " — par : " + utilisateur.getNomComplet());

        // Auto-résolution ALERTE_CRITIQUE sur ce tunnel si une intervention vient d'être créée
        if (auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_CRITIQUE", tunnel.getId())) {
            auditLogService.save("ALERTE_TRAITEE", tunnel.getId(), "TUNNEL", utilisateur,
                    tunnel.getLibelle() + " — alerte CRITIQUE résolue automatiquement"
                    + " — intervention " + intervention.getReference() + " créée"
                    + " — par : " + utilisateur.getNomComplet());
        }

        return toDTO(intervention);
    }

    @Transactional
    public InterventionDTO update(Long id, InterventionDTO dto, Utilisateur utilisateur) {
        Intervention intervention = findById(id);

        boolean isCreateur = utilisateur != null && intervention.getCreateur() != null
                && utilisateur.getId().equals(intervention.getCreateur().getId());

        boolean nonModifiable = intervention.getStatut() == InterventionStatut.CLOTUREE
                || intervention.getStatut() == InterventionStatut.ANNULEE;

        if (nonModifiable) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Modification impossible : l'intervention est " + intervention.getStatut());
        }
        if (!isCreateur) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Modification impossible : vous n'êtes pas le créateur de cette intervention");
        }

        // Bloquer la modification de la date de début si l'intervention est EN_COURS
        if (dto.getDatePrevue() != null && intervention.getStatut() == InterventionStatut.EN_COURS) {
            LocalDate nouvelleDate = LocalDate.parse(dto.getDatePrevue());
            if (!nouvelleDate.equals(intervention.getDatePrevue())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La date de début ne peut pas être modifiée une fois l'intervention démarrée");
            }
        }

        boolean datesModifiees = dto.getDatePrevue() != null || dto.getDateFinPrevue() != null;

        LocalDate newDebut = dto.getDatePrevue() != null ? LocalDate.parse(dto.getDatePrevue()) : intervention.getDatePrevue();
        LocalDate newFin = (dto.getDateFinPrevue() != null && !dto.getDateFinPrevue().isBlank())
                ? LocalDate.parse(dto.getDateFinPrevue()) : intervention.getDateFinPrevue();
        if (newFin != null && !newFin.isAfter(newDebut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de fin prévue doit être postérieure à la date de début");
        }

        if (dto.getDatePrevue() != null) intervention.setDatePrevue(newDebut);
        if (dto.getDateFinPrevue() != null && !dto.getDateFinPrevue().isBlank())
            intervention.setDateFinPrevue(newFin);
        if (dto.getDescription() != null) intervention.setDescription(dto.getDescription());
        if (dto.getCompetencesRequises() != null) intervention.setCompetencesRequises(dto.getCompetencesRequises());

        final Intervention saved = interventionRepository.save(intervention);

        auditLogService.save("MODIFICATION", id, "INTERVENTION", utilisateur,
                saved.getReference() + " modifiée par " + utilisateur.getNomComplet());

        // Après modification des dates : réévaluer tous les conflits actifs (symétriquement)
        if (datesModifiees) {
            List<Intervention> actives = interventionRepository.findByStatutIn(
                    List.of(InterventionStatut.PLANIFIEE, InterventionStatut.EN_COURS, InterventionStatut.SUSPENDUE));

            // 1. Résoudre tous les ALERTE_CONFLIT dont le conflit n'existe plus (inclut l'autre CDM)
            List<Long> idsConflitActifs = auditLogRepository.findInterventionIdsWithActiveConflit();
            for (Long conflitId : idsConflitActifs) {
                Intervention inter = actives.stream()
                        .filter(a -> a.getId().equals(conflitId)).findFirst().orElse(null);
                if (inter == null) continue;
                Set<Long> userIds = inter.getAffectations() != null
                        ? inter.getAffectations().stream()
                               .map(a -> a.getUtilisateur().getId()).collect(Collectors.toSet())
                        : Set.of();
                if (userIds.isEmpty()) continue;
                boolean conflitPersiste = actives.stream()
                        .filter(other -> !other.getId().equals(conflitId))
                        .filter(other -> datesOverlap(inter, other))
                        .anyMatch(other -> other.getAffectations() != null
                                && other.getAffectations().stream()
                                       .anyMatch(a -> userIds.contains(a.getUtilisateur().getId())));
                if (!conflitPersiste) {
                    auditLogService.save("ALERTE_TRAITEE", conflitId, "INTERVENTION", utilisateur,
                            inter.getReference() + " — alerte CONFLIT résolue automatiquement après décalage de "
                            + saved.getReference() + " — par : " + utilisateur.getNomComplet());
                }
            }

            // 2. Détecter si les nouvelles dates créent un nouveau conflit pour cette intervention
            List<Affectation> savedAffs = saved.getAffectations() != null ? saved.getAffectations() : List.of();
            if (!savedAffs.isEmpty()) {
                Set<Long> savedUserIds = savedAffs.stream()
                        .map(a -> a.getUtilisateur().getId()).collect(Collectors.toSet());
                boolean nouveauConflit = actives.stream()
                        .filter(other -> !other.getId().equals(id))
                        .filter(other -> datesOverlap(saved, other))
                        .anyMatch(other -> other.getAffectations() != null
                                && other.getAffectations().stream()
                                       .anyMatch(a -> savedUserIds.contains(a.getUtilisateur().getId())));
                if (nouveauConflit && !auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_CONFLIT", id)) {
                    auditLogService.save("ALERTE_CONFLIT", id, "INTERVENTION", utilisateur,
                            saved.getReference() + " — conflit de planning détecté après modification des dates"
                            + " — par : " + utilisateur.getNomComplet());
                }
            }
        }

        return toDTO(saved);
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
                if (utilisateur != null && utilisateur.getStatut() == UserStatut.INDISPONIBLE) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Vous êtes déclaré indisponible — vous ne pouvez pas démarrer ou reprendre une intervention");
                }
                // Vérifier qu'aucun membre de l'équipe n'est encore INDISPONIBLE
                if (ancien == InterventionStatut.SUSPENDUE && intervention.getAffectations() != null) {
                    List<String> indispos = intervention.getAffectations().stream()
                            .filter(a -> a.getUtilisateur().getStatut() == UserStatut.INDISPONIBLE)
                            .map(a -> a.getUtilisateur().getNomComplet())
                            .collect(Collectors.toList());
                    if (!indispos.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Impossible de reprendre : " + String.join(", ", indispos)
                                + " est/sont encore indisponible(s). Remplacez-les d'abord.");
                    }
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
                .filter(d -> d.getDisponibilite().equals("DISPONIBLE"))
                .collect(Collectors.toList());
    }

    /**
     * Pour un remplacement : retourne les disponibles ayant au moins une compétence du remplacé,
     * triés par nombre de compétences couvertes (desc).
     */
    public List<UtilisateurDispoDTO> getIntervenantsDisponiblesForRemplacement(Long interventionId, Long remplacerUserId) {
        Intervention intervention = findById(interventionId);

        // Compétences requises par l'intervention
        Set<String> compsRequises = parseComps(intervention.getCompetencesRequises());

        // Compétences de l'ingénieur remplacé
        Utilisateur remplace = utilisateurRepository.findById(remplacerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingénieur non trouvé"));
        Set<String> compsRemplace = parseComps(remplace.getCompetences());

        // Compétences requises que le remplacé apportait (intersection remplacé ∩ requises)
        Set<String> compsRequisesDuRemplace = new HashSet<>(compsRemplace);
        compsRequisesDuRemplace.retainAll(compsRequises);

        // Compétences requises déjà couvertes par le reste de l'équipe (sans le remplacé)
        Set<String> dejaCouverts = intervention.getAffectations() != null
                ? intervention.getAffectations().stream()
                        .filter(a -> !a.getUtilisateur().getId().equals(remplacerUserId))
                        .flatMap(a -> parseComps(a.getUtilisateur().getCompetences()).stream())
                        .collect(Collectors.toSet())
                : new HashSet<>();
        dejaCouverts.retainAll(compsRequises); // garder seulement celles qui sont requises

        // Compétences requises manquantes après retrait du remplacé
        Set<String> compsManquantes = new HashSet<>(compsRequisesDuRemplace);
        compsManquantes.removeAll(dejaCouverts);

        // Cible : compétences manquantes si non-vide, sinon toutes les requises du remplacé
        // Si le remplacé ne couvrait aucune requise, proposer des candidats avec n'importe quelle requise
        Set<String> cible = !compsManquantes.isEmpty() ? compsManquantes
                : !compsRequisesDuRemplace.isEmpty() ? compsRequisesDuRemplace
                : compsRequises;

        return utilisateurRepository.findByRoleAndStatut(UserRole.INGENIEUR, UserStatut.ACTIF)
                .stream()
                .filter(u -> !u.getId().equals(remplacerUserId))
                .filter(u -> intervention.getAffectations() == null
                        || intervention.getAffectations().stream().noneMatch(a -> a.getUtilisateur().getId().equals(u.getId())))
                .map(u -> computeDispoWithScore(u, intervention, cible, compsManquantes))
                .filter(d -> d.getDisponibilite().equals("DISPONIBLE"))
                .filter(d -> !d.getCompetencesCouvrees().isEmpty()) // couvre au moins 1 compétence cible
                .sorted(Comparator.comparingInt((UtilisateurDispoDTO d) -> d.getCompetencesManquantes().size()))
                .collect(Collectors.toList());
    }

    private Set<String> parseComps(String competences) {
        if (competences == null || competences.isBlank()) return new HashSet<>();
        return Arrays.stream(competences.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * @param cible        compétences que le candidat doit couvrir (compétences cibles)
     * @param manquantes   compétences encore manquantes après retrait du remplacé (pour scoring)
     */
    private UtilisateurDispoDTO computeDispoWithScore(Utilisateur u, Intervention intervention,
                                                       Set<String> cible, Set<String> manquantes) {
        UtilisateurDispoDTO base = computeDispo(u, intervention);
        Set<String> candidateComps = parseComps(u.getCompetences());

        // Compétences cibles que le candidat couvre
        List<String> couvertes = cible.stream()
                .filter(candidateComps::contains).collect(Collectors.toList());
        // Compétences encore manquantes même avec ce candidat
        List<String> encoreManquantes = manquantes.stream()
                .filter(c -> !candidateComps.contains(c)).collect(Collectors.toList());

        base.setCompetencesCouvrees(couvertes);
        base.setCompetencesManquantes(encoreManquantes);
        return base;
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

        // Auto-résolution alerte PREVENTIF (plus d'équipe sans affectation)
        if (auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_PREVENTIF", interventionId)) {
            auditLogService.save("ALERTE_TRAITEE", interventionId, "INTERVENTION", cdm,
                    intervention.getReference() + " — alerte PREVENTIF résolue automatiquement : équipe affectée"
                    + " — par : " + cdm.getNomComplet());
        }

        // Auto-résolution alerte CONFLIT si plus de conflit après réaffectation
        if (auditLogRepository.existsByTypeActionAndIdObjet("ALERTE_CONFLIT", interventionId)) {
            List<Long> newUserIds = dtos.stream().map(AffectationRequestDTO::getUtilisateurId).collect(Collectors.toList());
            boolean conflitPersiste = interventionRepository.findByStatutIn(
                    List.of(InterventionStatut.PLANIFIEE, InterventionStatut.EN_COURS, InterventionStatut.SUSPENDUE))
                .stream()
                .filter(other -> !other.getId().equals(interventionId))
                .filter(other -> datesOverlap(intervention, other))
                .anyMatch(other -> other.getAffectations().stream()
                    .anyMatch(a -> newUserIds.contains(a.getUtilisateur().getId())));
            if (!conflitPersiste) {
                auditLogService.save("ALERTE_TRAITEE", interventionId, "INTERVENTION", cdm,
                        intervention.getReference() + " — alerte CONFLIT résolue automatiquement : conflit résorbé"
                        + " — par : " + cdm.getNomComplet());
            }
        }

        // Lien automatique : si le tunnel de cette intervention a un AlerteAssignment ASSIGNEE,
        // on le lie automatiquement à cette intervention et on le marque TRAITEE
        if (intervention.getTunnel() != null) {
            alerteAssignmentRepository.findActiveByTunnelId(intervention.getTunnel().getId())
                    .forEach(assignment -> {
                        assignment.setIntervention(intervention);
                        assignment.setStatut("TRAITEE");
                        assignment.setTraiteAt(java.time.LocalDateTime.now());
                        alerteAssignmentRepository.save(assignment);
                        auditLogService.save("ALERTE_RESOLUE", assignment.getObjetId(), "TUNNEL", cdm,
                                "Alerte " + assignment.getNiveau() + " résolue automatiquement — "
                                + "intervention planifiée : " + intervention.getReference()
                                + " — par : " + cdm.getNomComplet());
                    });
        }

        return toDTO(findById(interventionId));
    }

    @Transactional
    public InterventionDTO remplacerMembre(Long interventionId, Long ancienUserId, Long nouvelUserId, Utilisateur cdm) {
        Intervention intervention = findById(interventionId);

        if (intervention.getStatut() == InterventionStatut.CLOTUREE
                || intervention.getStatut() == InterventionStatut.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de modifier l'équipe d'une intervention clôturée ou annulée");
        }

        Affectation ancienne = intervention.getAffectations().stream()
                .filter(a -> a.getUtilisateur().getId().equals(ancienUserId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cet intervenant n'est pas affecté à cette intervention"));

        Utilisateur ancien = ancienne.getUtilisateur();
        Utilisateur nouveau = utilisateurRepository.findById(nouvelUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nouvel intervenant introuvable"));

        String roleAncien = ancienne.getRole();

        // Remplacer : supprimer l'ancienne affectation, créer la nouvelle
        affectationRepository.delete(ancienne);

        Affectation nouvelleAff = Affectation.builder()
                .intervention(intervention)
                .utilisateur(nouveau)
                .role(roleAncien)
                .motif("Remplacement de " + ancien.getNomComplet() + " (indisponible)")
                .remplaceLId(ancienUserId)
                .build();
        affectationRepository.save(nouvelleAff);

        // Notifier l'ingénieur remplacé
        auditLogService.save("NOTIFICATION_REMPLACEMENT", ancien.getId(), "UTILISATEUR", cdm,
                "Vous avez été remplacé dans l'intervention " + intervention.getReference()
                + " — tunnel : " + (intervention.getTunnel() != null ? intervention.getTunnel().getLibelle() : "?")
                + " — remplacé par : " + nouveau.getNomComplet()
                + " — chargé de mission : " + cdm.getNomComplet());

        auditLogService.save("REMPLACEMENT_MEMBRE", interventionId, "INTERVENTION", cdm,
                intervention.getReference() + " — " + ancien.getNomComplet()
                + " remplacé par " + nouveau.getNomComplet()
                + " (rôle : " + roleAncien + ")"
                + " — par : " + cdm.getNomComplet());

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

        boolean resumePresent = (intervention.getResume() != null && !intervention.getResume().isBlank());
        if (!resumePresent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un résumé de mission est obligatoire pour clôturer l'intervention");
        }

        int nbNc = 0;
        int nbCritiques = 0;
        String referenceCorrectif = null;
        Long correctifId = null;

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

                // R5: CRITIQUE -> create corrective intervention + alert
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
                    correctifId = corrective.getId();

                    auditLogService.save("CREATION_CORRECTIVE", corrective.getId(), "INTERVENTION", null,
                            "Intervention corrective " + corrective.getReference()
                            + " créée automatiquement suite à NC critique sur " + intervention.getReference()
                            + " — tunnel : " + intervention.getTunnel().getLibelle());

                    // Alerte pour l'admin : NC critique déclarée → corrective à planifier
                    String descTruncatedNc = ncDto.getDescription().length() > 80
                            ? ncDto.getDescription().substring(0, 80) + "..." : ncDto.getDescription();
                    auditLogService.save("ALERTE_NC_CRITIQUE", corrective.getId(), "INTERVENTION", null,
                            corrective.getReference()
                            + " — intervention corrective requise suite à NC critique sur "
                            + intervention.getReference()
                            + " — tunnel : " + intervention.getTunnel().getLibelle()
                            + " — NC : " + descTruncatedNc
                            + " — déclarée par : " + utilisateur.getNomComplet());
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

        // Auto-résolution : PREVENTIF_RETARD et CONFLIT sur cette intervention
        for (String type : List.of("ALERTE_PREVENTIF_RETARD", "ALERTE_CONFLIT", "ALERTE_INFO")) {
            if (auditLogRepository.existsByTypeActionAndIdObjet(type, id)) {
                auditLogService.save("ALERTE_TRAITEE", id, "INTERVENTION", utilisateur,
                        intervention.getReference() + " — alerte " + type.replace("ALERTE_", "")
                        + " résolue automatiquement à la clôture — par : " + utilisateur.getNomComplet());
            }
        }

        return CloturerResponse.builder()
                .intervention(toDTO(findById(id)))
                .referenceCorrectif(referenceCorrectif)
                .correctifId(correctifId)
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
                .statut(a.getUtilisateur().getStatut() != null ? a.getUtilisateur().getStatut().name() : null)
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

    private boolean datesOverlap(Intervention i1, Intervention i2) {
        LocalDate start1 = i1.getDatePrevue();
        LocalDate end1 = i1.getDateFinPrevue() != null ? i1.getDateFinPrevue() : start1;
        LocalDate start2 = i2.getDatePrevue();
        LocalDate end2 = i2.getDateFinPrevue() != null ? i2.getDateFinPrevue() : start2;
        if (start1 == null || start2 == null) return false;
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    private boolean datesOverlap(LocalDate start1, LocalDate end1, Intervention other) {
        LocalDate start2 = other.getDatePrevue();
        LocalDate end2 = other.getDateFinPrevue() != null ? other.getDateFinPrevue() : start2;
        if (start1 == null || start2 == null) return false;
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    /** Retourne la liste des interventions en conflit de planning avec les dates données */
    public List<InterventionDTO> verifierConflitsParDates(Long interventionId, LocalDate datePrevue, LocalDate dateFinPrevue) {
        LocalDate fin = dateFinPrevue != null ? dateFinPrevue : datePrevue;
        Intervention courante = findById(interventionId);
        Set<Long> userIds = courante.getAffectations() != null
                ? courante.getAffectations().stream().map(a -> a.getUtilisateur().getId()).collect(Collectors.toSet())
                : Set.of();

        return interventionRepository.findByStatutIn(
                List.of(InterventionStatut.PLANIFIEE, InterventionStatut.EN_COURS, InterventionStatut.SUSPENDUE))
            .stream()
            .filter(other -> !other.getId().equals(interventionId))
            .filter(other -> datesOverlap(datePrevue, fin, other))
            .filter(other -> other.getAffectations() != null
                    && other.getAffectations().stream()
                       .anyMatch(a -> userIds.contains(a.getUtilisateur().getId())))
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
}
