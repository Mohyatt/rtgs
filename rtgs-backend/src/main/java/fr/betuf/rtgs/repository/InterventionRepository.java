package fr.betuf.rtgs.repository;

import fr.betuf.rtgs.entity.Intervention;
import fr.betuf.rtgs.entity.enums.InterventionStatut;
import fr.betuf.rtgs.entity.enums.InterventionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByStatutOrderByDatePrevueDesc(InterventionStatut statut);

    List<Intervention> findByTunnelIdOrderByDatePrevueDesc(Long tunnelId);

    boolean existsByTunnelIdAndStatutIn(Long tunnelId, List<InterventionStatut> statuts);

    List<Intervention> findByStatutAndDatePrevueBefore(InterventionStatut statut, LocalDate date);

    List<Intervention> findByStatutIn(List<InterventionStatut> statuts);

    List<Intervention> findByDatePrevueBetween(LocalDate from, LocalDate to);

    Optional<Intervention> findByReference(String reference);

    List<Intervention> findAllByOrderByDatePrevueDesc();

    @Query("SELECT i FROM Intervention i WHERE i.tunnel.id = :tunnelId " +
           "AND i.statut IN ('PLANIFIEE', 'EN_COURS') " +
           "AND i.type = :type " +
           "AND i.datePrevue BETWEEN :from AND :to")
    List<Intervention> findActiveByTunnelAndTypeInPeriod(@Param("tunnelId") Long tunnelId,
                                                          @Param("type") InterventionType type,
                                                          @Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);

    @Query("SELECT COUNT(i) FROM Intervention i WHERE i.datePrevue BETWEEN :from AND :to")
    long countByDatePrevueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(i) FROM Intervention i WHERE " +
           "(i.statut = 'PLANIFIEE' AND i.datePrevue < :today) OR " +
           "(i.statut = 'EN_COURS' AND i.dateFinPrevue IS NOT NULL AND i.dateFinPrevue < :today)")
    long countEnRetard(@Param("today") LocalDate today);

    @Query("SELECT DISTINCT i FROM Intervention i JOIN i.affectations a WHERE a.utilisateur.id = :userId ORDER BY i.datePrevue DESC")
    List<Intervention> findByAffectationUserId(@Param("userId") Long userId);

    List<Intervention> findByCreateurIdOrderByDatePrevueDesc(Long createurId);

    @Query("SELECT COUNT(i) FROM Intervention i WHERE i.createur.id = :cdmId AND i.datePrevue BETWEEN :from AND :to")
    long countByCreateurIdAndDatePrevueBetween(@Param("cdmId") Long cdmId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(i) FROM Intervention i WHERE i.createur.id = :cdmId AND " +
           "((i.statut = 'PLANIFIEE' AND i.datePrevue < :today) OR " +
           "(i.statut = 'EN_COURS' AND i.dateFinPrevue IS NOT NULL AND i.dateFinPrevue < :today))")
    long countEnRetardByCreateurId(@Param("cdmId") Long cdmId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(i) FROM Intervention i WHERE i.createur.id = :cdmId AND i.statut = 'CLOTUREE'")
    long countClotureesByCreateurId(@Param("cdmId") Long cdmId);

    @Query("SELECT COUNT(i) FROM Intervention i WHERE i.createur.id = :cdmId AND i.statut IN ('PLANIFIEE', 'EN_COURS')")
    long countActivesByCreateurId(@Param("cdmId") Long cdmId);

    @Query("SELECT COUNT(i) FROM Intervention i WHERE i.createur.id = :cdmId AND i.statut = 'A_CLOTURER'")
    long countACloturerByCreateurId(@Param("cdmId") Long cdmId);

    long countByCreateurId(Long createurId);
}
