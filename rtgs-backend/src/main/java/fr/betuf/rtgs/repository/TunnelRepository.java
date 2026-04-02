package fr.betuf.rtgs.repository;

import fr.betuf.rtgs.entity.Tunnel;
import fr.betuf.rtgs.entity.enums.TunnelStatut;
import fr.betuf.rtgs.entity.enums.TunnelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TunnelRepository extends JpaRepository<Tunnel, Long> {

    List<Tunnel> findByStatut(TunnelStatut statut);

    List<Tunnel> findByStatutAndLongueurGreaterThan(TunnelStatut statut, double longueur);

    @Query("SELECT t FROM Tunnel t WHERE t.statut = 'ACTIF' AND t.longueur > 1000 " +
           "AND (t.dateDerniereVisite IS NULL OR t.dateDerniereVisite < :cutoff)")
    List<Tunnel> findTunnelsOverdueForInspection(@Param("cutoff") LocalDate cutoff);

    @Query("SELECT t FROM Tunnel t WHERE " +
           "(:departement IS NULL OR t.departement LIKE %:departement%) AND " +
           "(:type IS NULL OR t.type = :type) AND " +
           "(:longueurMin IS NULL OR t.longueur >= :longueurMin) AND " +
           "(:longueurMax IS NULL OR t.longueur <= :longueurMax) AND " +
           "(:statut IS NULL OR t.statut = :statut)")
    List<Tunnel> rechercher(@Param("departement") String departement,
                            @Param("type") TunnelType type,
                            @Param("longueurMin") Double longueurMin,
                            @Param("longueurMax") Double longueurMax,
                            @Param("statut") TunnelStatut statut);
}
