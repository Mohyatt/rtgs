package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.AdminCreateUserRequestDTO;
import fr.betuf.rtgs.dto.AdminUpdateUserRequestDTO;
import fr.betuf.rtgs.dto.UtilisateurDTO;
import fr.betuf.rtgs.entity.Affectation;
import fr.betuf.rtgs.entity.Intervention;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.entity.enums.InterventionStatut;
import fr.betuf.rtgs.entity.enums.UserRole;
import fr.betuf.rtgs.entity.enums.UserStatut;
import fr.betuf.rtgs.repository.AffectationRepository;
import fr.betuf.rtgs.repository.InterventionRepository;
import fr.betuf.rtgs.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
@Slf4j
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final AffectationRepository affectationRepository;
    private final InterventionRepository interventionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public List<UtilisateurDTO> getAll(String role, String statut) {
        UserRole parsedRole = parseRole(role);
        UserStatut parsedStatut = parseStatut(statut);

        List<Utilisateur> users;
        if (parsedRole != null && parsedStatut != null) {
            users = utilisateurRepository.findByRoleAndStatut(parsedRole, parsedStatut);
        } else if (parsedRole != null) {
            users = utilisateurRepository.findByRole(parsedRole);
        } else if (parsedStatut != null) {
            users = utilisateurRepository.findByStatut(parsedStatut);
        } else {
            users = utilisateurRepository.findAll();
        }

        return users.stream().map(this::toDto).toList();
    }

    public UtilisateurDTO getById(Long id) {
        return toDto(findById(id));
    }

    public UtilisateurDTO getByEmail(String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));
        return toDto(user);
    }

    @Transactional
    public UtilisateurDTO createUser(AdminCreateUserRequestDTO request, String adminEmail) {
        if (isBlank(request.getNom()) || isBlank(request.getPrenom()) || isBlank(request.getEmail())
                || isBlank(request.getMotDePasse()) || isBlank(request.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "nom, prenom, email, motDePasse et role sont obligatoires");
        }
        if (request.getMotDePasse().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe doit contenir au moins 8 caractères");
        }
        if (utilisateurRepository.findByEmail(request.getEmail().trim().toLowerCase()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un utilisateur avec cet email existe déjà");
        }

        UserRole role = parseRoleRequired(request.getRole());
        UserStatut statut = parseStatut(request.getStatut());
        if (statut == null) {
            statut = UserStatut.ACTIF;
        }

        Utilisateur user = Utilisateur.builder()
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .email(request.getEmail().trim().toLowerCase())
                .motDePasse(passwordEncoder.encode(request.getMotDePasse()))
                .role(role)
                .statut(statut)
                .competences(request.getCompetences())
                .pole(request.getPole())
                .organisation(request.getOrganisation())
                .build();
        Utilisateur created = utilisateurRepository.save(user);

        Utilisateur admin = getByEmailOrNull(adminEmail);
        auditLogService.save("ADMIN_CREATION_UTILISATEUR", created.getId(), "UTILISATEUR", admin,
                "Création utilisateur " + created.getNomComplet() + " (" + created.getRole() + ")");
        return toDto(created);
    }

    @Transactional
    public UtilisateurDTO updateUser(Long id, AdminUpdateUserRequestDTO request, String adminEmail) {
        Utilisateur user = findById(id);

        if (!isBlank(request.getEmail())) {
            String email = request.getEmail().trim().toLowerCase();
            utilisateurRepository.findByEmail(email)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Un utilisateur avec cet email existe déjà");
                    });
            user.setEmail(email);
        }
        if (!isBlank(request.getNom())) user.setNom(request.getNom().trim());
        if (!isBlank(request.getPrenom())) user.setPrenom(request.getPrenom().trim());
        if (!isBlank(request.getRole())) user.setRole(parseRoleRequired(request.getRole()));
        if (!isBlank(request.getStatut())) user.setStatut(parseStatutRequired(request.getStatut()));
        if (request.getCompetences() != null) user.setCompetences(request.getCompetences());
        if (request.getPole() != null) user.setPole(request.getPole());
        if (request.getOrganisation() != null) user.setOrganisation(request.getOrganisation());

        Utilisateur saved = utilisateurRepository.save(user);
        Utilisateur admin = getByEmailOrNull(adminEmail);
        auditLogService.save("ADMIN_MODIFICATION_UTILISATEUR", saved.getId(), "UTILISATEUR", admin,
                "Mise à jour utilisateur " + saved.getNomComplet() + " (" + saved.getRole() + ", " + saved.getStatut() + ")");
        return toDto(saved);
    }

    @Transactional
    public UtilisateurDTO updateRole(Long id, String role, String adminEmail) {
        Utilisateur user = findById(id);
        user.setRole(parseRoleRequired(role));
        Utilisateur saved = utilisateurRepository.save(user);

        Utilisateur admin = getByEmailOrNull(adminEmail);
        auditLogService.save("ADMIN_CHANGEMENT_ROLE", saved.getId(), "UTILISATEUR", admin,
                "Changement de rôle: " + saved.getNomComplet() + " -> " + saved.getRole());
        return toDto(saved);
    }

    @Transactional
    public UtilisateurDTO updateStatut(Long id, String statut, String adminEmail) {
        Utilisateur user = findById(id);
        user.setStatut(parseStatutRequired(statut));
        Utilisateur saved = utilisateurRepository.save(user);

        Utilisateur admin = getByEmailOrNull(adminEmail);
        auditLogService.save("ADMIN_CHANGEMENT_STATUT", saved.getId(), "UTILISATEUR", admin,
                "Changement de statut: " + saved.getNomComplet() + " -> " + saved.getStatut());
        return toDto(saved);
    }

    @Transactional
    public void updatePassword(Long id, String newPassword, String adminEmail) {
        if (isBlank(newPassword) || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe doit contenir au moins 8 caractères");
        }
        Utilisateur user = findById(id);
        user.setMotDePasse(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);

        Utilisateur admin = getByEmailOrNull(adminEmail);
        auditLogService.save("ADMIN_RESET_MDP", user.getId(), "UTILISATEUR", admin,
                "Réinitialisation du mot de passe pour " + user.getNomComplet());
    }

    @Transactional
    public void deleteUser(Long id, String adminEmail) {
        Utilisateur user = findById(id);
        if (adminEmail != null && adminEmail.equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas supprimer votre propre compte");
        }

        Utilisateur admin = getByEmailOrNull(adminEmail);
        try {
            utilisateurRepository.delete(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Suppression impossible: utilisateur référencé dans des données métier");
        }

        auditLogService.save("ADMIN_SUPPRESSION_UTILISATEUR", id, "UTILISATEUR", admin,
                "Suppression utilisateur " + user.getNomComplet() + " (" + user.getEmail() + ")");
    }

    private Utilisateur findById(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));
    }

    private Utilisateur getByEmailOrNull(String email) {
        if (email == null || email.isBlank()) return null;
        return utilisateurRepository.findByEmail(email).orElse(null);
    }

    @Transactional
    public Map<String, Object> toggleDisponibilite(String statut, String motif, String dateRetour, String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur non trouvé"));

        if (user.getRole() != UserRole.INGENIEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul un ingénieur peut signaler sa disponibilité");
        }

        if (!"INDISPONIBLE".equalsIgnoreCase(statut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vous ne pouvez déclarer que votre indisponibilité. Le retour est automatique à la date prévue.");
        }

        if (user.getStatut() == UserStatut.INDISPONIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous êtes déjà déclaré indisponible. Votre retour sera automatique à la date prévue.");
        }

        if (motif == null || motif.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le motif est obligatoire pour déclarer une indisponibilité");
        }

        UserStatut newStatut = UserStatut.INDISPONIBLE;
        UserStatut ancien = user.getStatut();

        // Sauvegarder la date de retour et le motif sur l'entité
        LocalDate retourDate = null;
        if (dateRetour != null && !dateRetour.isBlank()) {
            try {
                retourDate = LocalDate.parse(dateRetour);
            } catch (Exception ignored) {}
        }
        user.setStatut(newStatut);
        user.setDateRetour(retourDate);
        user.setMotifIndispo(motif);
        utilisateurRepository.save(user);

        String motifLog = (motif != null && !motif.isBlank()) ? motif : "non précisé";
        String retourLog = (dateRetour != null && !dateRetour.isBlank()) ? dateRetour : "non précisée";

        auditLogService.save("CHANGEMENT_DISPONIBILITE", user.getId(), "UTILISATEUR", user,
                user.getNomComplet() + " — statut changé : " + ancien + " → " + newStatut
                + " — motif : " + motifLog + " — retour prévu : " + retourLog);

        List<Map<String, Object>> missionsSuspendues = new ArrayList<>();

        if (newStatut == UserStatut.INDISPONIBLE) {
            LocalDate today = LocalDate.now();
            final LocalDate finalRetourDate = retourDate;

            List<Affectation> affectations = affectationRepository.findByUtilisateurId(user.getId());
            for (Affectation aff : affectations) {
                Intervention intervention = aff.getIntervention();
                if (intervention.getStatut() == InterventionStatut.PLANIFIEE
                        || intervention.getStatut() == InterventionStatut.EN_COURS) {

                    // Si une date de retour est fournie, ne suspendre que les interventions
                    // dont la période chevauche [today, dateRetour]
                    if (finalRetourDate != null) {
                        LocalDate datePrevue = intervention.getDatePrevue();
                        LocalDate dateFinPrevue = intervention.getDateFinPrevue();
                        // L'intervention chevauche la période d'indisponibilité si :
                        // datePrevue <= dateRetour ET (dateFinPrevue == null OU dateFinPrevue >= today)
                        boolean startsBeforeReturn = datePrevue != null && !datePrevue.isAfter(finalRetourDate);
                        boolean endsAfterToday = dateFinPrevue == null || !dateFinPrevue.isBefore(today);
                        if (!startsBeforeReturn || !endsAfterToday) {
                            continue; // Intervention hors de la période d'indisponibilité → pas suspendue
                        }
                    }

                    InterventionStatut ancienStatut = intervention.getStatut();
                    intervention.setStatut(InterventionStatut.SUSPENDUE);
                    interventionRepository.save(intervention);

                    String createurNom = intervention.getCreateur() != null
                            ? intervention.getCreateur().getNomComplet() : "inconnu";

                    auditLogService.save("CHANGEMENT_STATUT", intervention.getId(), "INTERVENTION", user,
                            intervention.getReference() + " — statut changé : "
                            + ancienStatut + " → SUSPENDUE"
                            + " — raison : indisponibilité de " + user.getNomComplet()
                            + " — motif : " + motifLog);

                    auditLogService.save("ALERTE_PREVENTIF", intervention.getId(), "INTERVENTION", null,
                            intervention.getReference()
                            + " — suspendue suite à l'indisponibilité de " + user.getNomComplet()
                            + " (" + aff.getRole() + ")"
                            + " — motif : " + motifLog
                            + " — retour prévu : " + retourLog
                            + " — chargé de mission : " + createurNom);

                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("interventionId", intervention.getId());
                    info.put("reference", intervention.getReference());
                    info.put("ancienStatut", ancienStatut.name());
                    info.put("role", aff.getRole());
                    info.put("tunnel", intervention.getTunnel() != null ? intervention.getTunnel().getLibelle() : null);
                    missionsSuspendues.add(info);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("utilisateur", toDto(user));
        result.put("missionsSuspendues", missionsSuspendues);
        result.put("nbMissionsSuspendues", missionsSuspendues.size());
        return result;
    }

    /**
     * Job planifié : chaque jour à minuit, restaure automatiquement le statut ACTIF
     * des ingénieurs dont la date de retour est atteinte.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void autoRetourDisponibilite() {
        LocalDate today = LocalDate.now();
        List<Utilisateur> indispos = utilisateurRepository.findByStatut(UserStatut.INDISPONIBLE);
        for (Utilisateur user : indispos) {
            if (user.getDateRetour() != null && !user.getDateRetour().isAfter(today)) {
                user.setStatut(UserStatut.ACTIF);
                user.setDateRetour(null);
                user.setMotifIndispo(null);
                utilisateurRepository.save(user);
                auditLogService.save("CHANGEMENT_DISPONIBILITE", user.getId(), "UTILISATEUR", user,
                        user.getNomComplet() + " — retour automatique : INDISPONIBLE → ACTIF"
                        + " — date de retour atteinte : " + today);
                log.info("Retour automatique de {} (dateRetour: {})", user.getNomComplet(), today);
            }
        }
    }

    private UtilisateurDTO toDto(Utilisateur user) {
        return UtilisateurDTO.builder()
                .id(user.getId())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .nomComplet(user.getNomComplet())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .statut(user.getStatut() != null ? user.getStatut().name() : null)
                .competences(user.getCompetences())
                .pole(user.getPole())
                .organisation(user.getOrganisation())
                .dateRetour(user.getDateRetour() != null ? user.getDateRetour().toString() : null)
                .motifIndispo(user.getMotifIndispo())
                .build();
    }

    private UserRole parseRoleRequired(String role) {
        UserRole parsed = parseRole(role);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètre role obligatoire");
        }
        return parsed;
    }

    private UserStatut parseStatutRequired(String statut) {
        UserStatut parsed = parseStatut(statut);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètre statut obligatoire");
        }
        return parsed;
    }

    private UserRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètre role invalide");
        }
    }

    private UserStatut parseStatut(String statut) {
        if (statut == null || statut.isBlank()) {
            return null;
        }
        try {
            return UserStatut.valueOf(statut.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètre statut invalide");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
