package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.AdminCreateUserRequestDTO;
import fr.betuf.rtgs.dto.AdminUpdatePasswordRequestDTO;
import fr.betuf.rtgs.dto.AdminUpdateUserRequestDTO;
import fr.betuf.rtgs.dto.UtilisateurDTO;
import fr.betuf.rtgs.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UtilisateurDTO>> getAll(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String statut) {
        return ResponseEntity.ok(utilisateurService.getAll(role, statut));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtilisateurDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(utilisateurService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtilisateurDTO> create(@RequestBody AdminCreateUserRequestDTO request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(utilisateurService.createUser(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtilisateurDTO> update(@PathVariable Long id,
                                                 @RequestBody AdminUpdateUserRequestDTO request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(utilisateurService.updateUser(id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtilisateurDTO> updateRole(@PathVariable Long id,
                                                     @RequestParam String role,
                                                     Authentication authentication) {
        return ResponseEntity.ok(utilisateurService.updateRole(id, role, authentication.getName()));
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtilisateurDTO> updateStatut(@PathVariable Long id,
                                                       @RequestParam String statut,
                                                       Authentication authentication) {
        return ResponseEntity.ok(utilisateurService.updateStatut(id, statut, authentication.getName()));
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updatePassword(@PathVariable Long id,
                                               @RequestBody AdminUpdatePasswordRequestDTO request,
                                               Authentication authentication) {
        utilisateurService.updatePassword(id, request.getMotDePasse(), authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        utilisateurService.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UtilisateurDTO> getMe(Authentication authentication) {
        return ResponseEntity.ok(utilisateurService.getByEmail(authentication.getName()));
    }

    @PatchMapping("/me/disponibilite")
    @PreAuthorize("hasRole('INGENIEUR')")
    public ResponseEntity<Map<String, Object>> toggleDisponibilite(
            @RequestParam String statut,
            @RequestParam(required = false) String motif,
            @RequestParam(required = false) String dateRetour,
            Authentication authentication) {
        return ResponseEntity.ok(utilisateurService.toggleDisponibilite(statut, motif, dateRetour, authentication.getName()));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> getRolesPermissions() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "role", "ADMIN",
                        "description", "Gère les utilisateurs, les droits et le système.",
                        "permissions", List.of(
                                "Voir toutes les interventions (lecture seule)",
                                "Voir et traiter les alertes (désigner un chargé de mission)",
                                "Gérer les utilisateurs (CRUD complet)",
                                "Accès dashboard global et audit",
                                "Pas de création d'intervention")
                ),
                Map.of(
                        "role", "CHARGE_MISSION",
                        "description", "Planifie les interventions et affecte les équipes.",
                        "permissions", List.of(
                                "Créer et modifier des interventions",
                                "Affecter / modifier / remplacer les équipes",
                                "Replanifier une intervention",
                                "Gérer les conflits de planning",
                                "Clôturer une intervention",
                                "Dashboard opérationnel")
                ),
                Map.of(
                        "role", "INGENIEUR",
                        "description", "Réalise les interventions et saisit les comptes rendus.",
                        "permissions", List.of(
                                "Voir ses missions affectées",
                                "Marquer une intervention en cours",
                                "Saisir le compte rendu et clôturer",
                                "Signaler sa disponibilité / indisponibilité",
                                "Consulter les documents liés")
                ),
                Map.of(
                        "role", "EXPLOITANT",
                        "description", "Consulte les informations et peut être impliqué côté terrain.",
                        "permissions", List.of(
                                "Consultation des tunnels",
                                "Consultation des interventions",
                                "Dashboard consultation et rapports")
                ),
                Map.of(
                        "role", "EXTERNE",
                        "description", "Accès limité à la consultation publique.",
                        "permissions", List.of(
                                "Consultation portail public des tunnels",
                                "Export de données (CSV)")
                )
        ));
    }
}
