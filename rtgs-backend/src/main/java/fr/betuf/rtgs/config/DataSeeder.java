package fr.betuf.rtgs.config;

import fr.betuf.rtgs.entity.*;
import fr.betuf.rtgs.entity.enums.*;
import fr.betuf.rtgs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final TunnelRepository tunnelRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final InterventionRepository interventionRepository;
    private final AffectationRepository affectationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (tunnelRepository.count() > 0) {
            log.info("Données déjà présentes, seed ignoré.");
            return;
        }
        log.info("Initialisation des données de démo...");

        String pwd = passwordEncoder.encode("rtgs2026");

        // ── TUNNELS ──────────────────────────────────────────────────────────
        Tunnel frejus = save(tunnel("Tunnel du Fréjus", "Savoie (73)", 6580.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 2, 15)));
        Tunnel epine  = save(tunnel("Tunnel de l'Épine", "Ain (01)", 4800.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 1, 20)));
        Tunnel roux   = save(tunnel("Tunnel du Roux", "Isère (38)", 2100.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 2, 1)));
        Tunnel montblanc = save(tunnel("Tunnel Mont Blanc", "Haute-Savoie (74)", 7640.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 10, 10)));
        Tunnel caluire = save(tunnel("Tunnel de Caluire", "Rhône (69)", 3728.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 11, 5)));
        Tunnel prado  = save(tunnel("Tunnel Prado-Carénage", "Bouches-du-Rhône (13)", 2455.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 12, 15)));
        Tunnel chat   = save(tunnel("Tunnel du Chat", "Savoie (73)", 1820.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 9, 20)));
        Tunnel chamoise = save(tunnel("Tunnel de Chamoise", "Jura (39)", 1540.0, 2, TunnelType.ROUTIER, TunnelStatut.ACTIF, LocalDate.of(2025, 8, 14)));

        // ── UTILISATEURS ─────────────────────────────────────────────────────
        Utilisateur axel    = save(user("Hère", "Axel", "axel.here@betuf.fr", pwd, UserRole.ADMIN, UserStatut.ACTIF, null, "Direction", null));
        Utilisateur isabelle = save(user("Pont", "Isabelle", "isabelle.pont@betuf.fr", pwd, UserRole.CHARGE_MISSION, UserStatut.ACTIF, "SECURITE,VENTILATION", "Exploitation", null));
        Utilisateur angela  = save(user("Doublesens", "Angela", "angela.d@betuf.fr", pwd, UserRole.INGENIEUR, UserStatut.ACTIF, "SECURITE,PCME", "Sécurité", null));
        Utilisateur ali     = save(user("Thinérairebis", "Ali", "ali.t@betuf.fr", pwd, UserRole.INGENIEUR, UserStatut.ACTIF, "VENTILATION,EEG", "Ventilation", null));
        Utilisateur louis   = save(user("Vragedar", "Louis", "louis.v@betuf.fr", pwd, UserRole.INGENIEUR, UserStatut.ACTIF, "MATERIAUX,SECURITE", "Matériaux", null));
        Utilisateur oscar   = save(user("Four", "Oscar", "oscar.f@betuf.fr", pwd, UserRole.INGENIEUR, UserStatut.ACTIF, "EXPLOITATION,SECURITE", "Exploitation", null));
        Utilisateur romeo   = save(user("Taurout", "Roméo", "romeo.t@betuf.fr", pwd, UserRole.INGENIEUR, UserStatut.INDISPONIBLE, "SECURITE", "Sécurité", null));
        Utilisateur marc    = save(user("Vitesse", "Marc", "marc.v@betuf.fr", pwd, UserRole.INGENIEUR, UserStatut.ACTIF, "VENTILATION", "Ventilation", null));
        Utilisateur isabelleC = save(user("Carré", "Isabelle", "isabelle.c@betuf.fr", pwd, UserRole.INGENIEUR, UserStatut.ACTIF, "VENTILATION,GEOLOGIE", "Ventilation", null));
        Utilisateur jenny   = save(user("Civil", "Jenny", "jenny.civil@betuf.fr", pwd, UserRole.ADMIN, UserStatut.ACTIF, null, "Informatique", null));

        // ── INTERVENTIONS ────────────────────────────────────────────────────
        Intervention i112 = saveIntervention("INT-2026-0112", frejus, InterventionType.VISITE_SECURITE_ANNUELLE,
                InterventionStatut.PLANIFIEE, LocalDate.of(2026, 3, 22), LocalDate.of(2026, 3, 23),
                "SECURITE,VENTILATION", null);
        saveAffectation(i112, angela, "Chef de mission");
        saveAffectation(i112, ali, "Intervenant");

        Intervention i109 = saveIntervention("INT-2026-0109", caluire, InterventionType.CONTROLE_VENTILATION,
                InterventionStatut.EN_COURS, LocalDate.of(2026, 3, 8), null,
                "VENTILATION", null);
        saveAffectation(i109, marc, "Chef de mission");

        Intervention i098 = saveIntervention("INT-2026-0098", montblanc, InterventionType.EXPERTISE_GENIE_CIVIL,
                InterventionStatut.PLANIFIEE, LocalDate.of(2026, 3, 22), null,
                "MATERIAUX,GEOLOGIE", null);
        saveAffectation(i098, louis, "Chef de mission");

        Intervention i087 = saveIntervention("INT-2026-0087", prado, InterventionType.INSPECTION_PERIODIQUE,
                InterventionStatut.CLOTUREE, LocalDate.of(2026, 3, 2), null,
                "SECURITE", "Inspection périodique réalisée. Aucune anomalie constatée.");
        i087.setDateRealisation(LocalDate.of(2026, 3, 2));
        interventionRepository.save(i087);
        saveAffectation(i087, oscar, "Chef de mission");

        // ── AUDIT LOGS ────────────────────────────────────────────────────────
        auditLog("CREATION", i112.getId(), "INTERVENTION", isabelle,
                "INT-2026-0112 créée — tunnel : Tunnel du Fréjus — type : Visite sécurité annuelle — par : Isabelle Pont");
        auditLog("PLANIFICATION", i112.getId(), "INTERVENTION", isabelle,
                "INT-2026-0112 planifiée — équipe : Angela Doublesens, Ali Thinérairebis — par : Isabelle Pont");
        auditLog("ALERTE_CRITIQUE", frejus.getId(), "TUNNEL", null,
                "Tunnel du Fréjus — 387 jours sans visite réglementaire (limite : 365 jours) — dernière visite : 15/02/2025");
        auditLog("ALERTE_CRITIQUE", epine.getId(), "TUNNEL", null,
                "Tunnel de l'Épine — 421 jours sans visite réglementaire (limite : 365 jours) — dernière visite : 20/01/2025");
        auditLog("ALERTE_CRITIQUE", roux.getId(), "TUNNEL", null,
                "Tunnel du Roux — 398 jours sans visite réglementaire (limite : 365 jours) — dernière visite : 01/02/2025");
        auditLog("ALERTE_PREVENTIF", 847L, "INTERVENTION", null,
                "INT-2026-0847 planifiée le 29/03/2026 sans intervenant affecté — J-5 dépassé");
        auditLog("ALERTE_INFO", i098.getId(), "INTERVENTION", null,
                "INT-2026-0098 — compte rendu manquant pour clôture — en attente depuis 5 jours");
        auditLog("CLOTURE", i087.getId(), "INTERVENTION", oscar,
                "INT-2026-0087 clôturée — date réalisation : 02/03/2026 — 0 non-conformité(s) — par : Oscar Four");
        auditLog("CONNEXION", isabelle.getId(), "UTILISATEUR", isabelle,
                "Isabelle Pont (CHARGE_MISSION) connecté(e)");

        log.info("Données de démo initialisées avec succès.");
    }

    private Tunnel save(Tunnel t) { return tunnelRepository.save(t); }
    private Utilisateur save(Utilisateur u) { return utilisateurRepository.save(u); }

    private Tunnel tunnel(String libelle, String dept, double longueur, int nbTubes,
                          TunnelType type, TunnelStatut statut, LocalDate lastVisit) {
        return Tunnel.builder()
                .libelle(libelle).departement(dept).longueur(longueur).nbTubes(nbTubes)
                .type(type).statut(statut).dateDerniereVisite(lastVisit).build();
    }

    private Utilisateur user(String nom, String prenom, String email, String pwd,
                             UserRole role, UserStatut statut, String competences,
                             String pole, String organisation) {
        return Utilisateur.builder()
                .nom(nom).prenom(prenom).email(email).motDePasse(pwd)
                .role(role).statut(statut).competences(competences)
                .pole(pole).organisation(organisation).build();
    }

    private Intervention saveIntervention(String ref, Tunnel tunnel, InterventionType type,
                                          InterventionStatut statut, LocalDate datePrevue,
                                          LocalDate dateFinPrevue, String comps, String resume) {
        Intervention i = Intervention.builder()
                .reference(ref).tunnel(tunnel).type(type).statut(statut)
                .datePrevue(datePrevue).dateFinPrevue(dateFinPrevue)
                .competencesRequises(comps).resume(resume).build();
        return interventionRepository.save(i);
    }

    private void saveAffectation(Intervention i, Utilisateur u, String role) {
        affectationRepository.save(Affectation.builder()
                .intervention(i).utilisateur(u).role(role).build());
    }

    private void auditLog(String typeAction, Long idObjet, String typeObjet,
                          Utilisateur utilisateur, String details) {
        auditLogRepository.save(AuditLog.builder()
                .typeAction(typeAction).idObjet(idObjet).typeObjet(typeObjet)
                .utilisateur(utilisateur).details(details).build());
    }
}
