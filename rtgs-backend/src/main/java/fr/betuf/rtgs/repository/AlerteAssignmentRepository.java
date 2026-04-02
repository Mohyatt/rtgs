package fr.betuf.rtgs.repository;

import fr.betuf.rtgs.entity.AlerteAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlerteAssignmentRepository extends JpaRepository<AlerteAssignment, Long> {

    List<AlerteAssignment> findByChargeMissionIdOrderByCreatedAtDesc(Long chargeMissionId);

    List<AlerteAssignment> findByChargeMissionIdAndStatutOrderByCreatedAtDesc(Long chargeMissionId, String statut);

    long countByChargeMissionIdAndStatut(Long chargeMissionId, String statut);

    long countByChargeMissionId(Long chargeMissionId);

    @Query("SELECT COUNT(a) FROM AlerteAssignment a WHERE a.chargeMission.id = :cdmId AND a.intervention IS NOT NULL")
    long countTraiteesParCdm(@Param("cdmId") Long cdmId);

    List<AlerteAssignment> findByChargeMissionIdAndTraiteAtIsNotNull(Long chargeMissionId);
}
