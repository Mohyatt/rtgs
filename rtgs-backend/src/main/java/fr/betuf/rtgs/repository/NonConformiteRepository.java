package fr.betuf.rtgs.repository;

import fr.betuf.rtgs.entity.NonConformite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NonConformiteRepository extends JpaRepository<NonConformite, Long> {

    List<NonConformite> findByInterventionId(Long interventionId);

    long countByInterventionId(Long interventionId);
}
