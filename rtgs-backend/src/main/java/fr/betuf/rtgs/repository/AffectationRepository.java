package fr.betuf.rtgs.repository;

import fr.betuf.rtgs.entity.Affectation;
import fr.betuf.rtgs.entity.enums.InterventionStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AffectationRepository extends JpaRepository<Affectation, Long> {

    List<Affectation> findByInterventionId(Long interventionId);

    List<Affectation> findByUtilisateurId(Long utilisateurId);

    long countByInterventionId(Long interventionId);

    void deleteByInterventionId(Long interventionId);

    @Query("SELECT a FROM Affectation a WHERE a.utilisateur.id = :userId " +
           "AND a.intervention.statut IN ('PLANIFIEE', 'EN_COURS') " +
           "AND a.intervention.datePrevue <= :dateFin " +
           "AND (a.intervention.dateFinPrevue IS NULL OR a.intervention.dateFinPrevue >= :dateDebut)")
    List<Affectation> findConflictsForUser(@Param("userId") Long userId,
                                           @Param("dateDebut") LocalDate dateDebut,
                                           @Param("dateFin") LocalDate dateFin);

    @Query("SELECT COUNT(a) FROM Affectation a WHERE a.utilisateur.id = :userId " +
           "AND a.intervention.statut IN ('PLANIFIEE', 'EN_COURS')")
    long countActiveMissionsForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM Affectation a WHERE a.utilisateur.id = :userId " +
           "AND LOWER(a.role) LIKE '%chef%' AND a.intervention.statut IN ('PLANIFIEE', 'EN_COURS')")
    long countChefMissionActives(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM Affectation a WHERE a.utilisateur.id = :userId " +
           "AND LOWER(a.role) NOT LIKE '%chef%' AND a.intervention.statut IN ('PLANIFIEE', 'EN_COURS')")
    long countIntervenantActives(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM Affectation a WHERE a.utilisateur.id = :userId " +
           "AND a.intervention.statut = 'CLOTUREE'")
    long countClotureesMissionsForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM Affectation a WHERE a.utilisateur.id = :userId " +
           "AND a.intervention.statut IN ('PLANIFIEE', 'EN_COURS') " +
           "AND ((a.intervention.statut = 'PLANIFIEE' AND a.intervention.datePrevue < :today) " +
           "OR (a.intervention.statut = 'EN_COURS' AND a.intervention.dateFinPrevue IS NOT NULL AND a.intervention.dateFinPrevue < :today))")
    long countEnRetardMissionsForUser(@Param("userId") Long userId, @Param("today") LocalDate today);

    long countByUtilisateurId(Long userId);
}
