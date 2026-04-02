package fr.betuf.rtgs.repository;

import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.entity.enums.UserRole;
import fr.betuf.rtgs.entity.enums.UserStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmail(String email);

    List<Utilisateur> findByRole(UserRole role);

    List<Utilisateur> findByRoleAndStatut(UserRole role, UserStatut statut);

    List<Utilisateur> findByStatut(UserStatut statut);

    List<Utilisateur> findByRoleNot(UserRole role);
}
