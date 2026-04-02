package fr.betuf.rtgs.repository;

import fr.betuf.rtgs.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByDateHeureDesc();

    List<AuditLog> findByTypeActionOrderByDateHeureDesc(String typeAction);

    List<AuditLog> findByTypeObjetAndIdObjetOrderByDateHeureDesc(String typeObjet, Long idObjet);

    List<AuditLog> findByTypeActionContainingOrderByDateHeureDesc(String typeAction);

    boolean existsByTypeActionAndIdObjet(String typeAction, Long idObjet);

    @Query("SELECT a FROM AuditLog a WHERE a.typeAction LIKE 'ALERTE_%' " +
           "AND a.typeAction <> 'ALERTE_TRAITEE' " +
           "ORDER BY a.dateHeure DESC")
    List<AuditLog> findActiveAlertes();

    @Query("SELECT a FROM AuditLog a WHERE a.typeAction LIKE 'ALERTE_%' " +
           "AND a.typeAction <> 'ALERTE_TRAITEE' " +
           "AND (:niveau IS NULL OR a.typeAction = CONCAT('ALERTE_', :niveau)) " +
           "ORDER BY a.dateHeure DESC")
    List<AuditLog> findActiveAlertesByNiveau(@Param("niveau") String niveau);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.typeAction = :typeAction " +
           "AND a.idObjet NOT IN " +
           "(SELECT b.idObjet FROM AuditLog b WHERE b.typeAction = 'ALERTE_TRAITEE' AND b.idObjet IS NOT NULL)")
    long countActiveByTypeAction(@Param("typeAction") String typeAction);
}
