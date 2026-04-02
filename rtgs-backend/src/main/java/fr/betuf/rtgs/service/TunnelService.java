package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.InterventionDTO;
import fr.betuf.rtgs.dto.TunnelDTO;
import fr.betuf.rtgs.dto.TunnelPublicDTO;
import fr.betuf.rtgs.entity.Tunnel;
import fr.betuf.rtgs.entity.enums.TunnelStatut;
import fr.betuf.rtgs.entity.enums.TunnelType;
import fr.betuf.rtgs.repository.InterventionRepository;
import fr.betuf.rtgs.repository.TunnelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TunnelService {

    private final TunnelRepository tunnelRepository;
    private final InterventionRepository interventionRepository;

    public List<TunnelDTO> getAll() {
        return tunnelRepository.findByStatut(TunnelStatut.ACTIF)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public TunnelDTO getById(Long id) {
        return tunnelRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tunnel non trouvé"));
    }

    public Tunnel getTunnelEntity(Long id) {
        return tunnelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tunnel non trouvé"));
    }

    public List<TunnelDTO> rechercher(String departement, String type, Double longueurMin,
                                       Double longueurMax, String statut) {
        TunnelType typeEnum = type != null && !type.isBlank() ? TunnelType.valueOf(type) : null;
        TunnelStatut statutEnum = statut != null && !statut.isBlank() ? TunnelStatut.valueOf(statut) : null;
        return tunnelRepository.rechercher(departement, typeEnum, longueurMin, longueurMax, statutEnum)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<TunnelPublicDTO> getAllPublic() {
        return tunnelRepository.findByStatut(TunnelStatut.ACTIF)
                .stream().map(this::toPublicDTO).collect(Collectors.toList());
    }

    public TunnelPublicDTO getPublicById(Long id) {
        return tunnelRepository.findById(id)
                .map(this::toPublicDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tunnel non trouvé"));
    }

    public TunnelDTO toDTO(Tunnel t) {
        return TunnelDTO.builder()
                .id(t.getId())
                .libelle(t.getLibelle())
                .departement(t.getDepartement())
                .longueur(t.getLongueur())
                .nbTubes(t.getNbTubes())
                .statut(t.getStatut() != null ? t.getStatut().name() : null)
                .type(t.getType() != null ? t.getType().name() : null)
                .dateDerniereVisite(t.getDateDerniereVisite() != null ? t.getDateDerniereVisite().toString() : null)
                .exploitant(t.getExploitant())
                .build();
    }

    private TunnelPublicDTO toPublicDTO(Tunnel t) {
        String statutConformite = "CONFORME";
        if (t.getDateDerniereVisite() == null) {
            statutConformite = "EN_RETARD";
        } else {
            long days = java.time.temporal.ChronoUnit.DAYS.between(t.getDateDerniereVisite(), LocalDate.now());
            if (days > 365) statutConformite = "EN_RETARD";
            else if (days > 300) statutConformite = "A_PLANIFIER";
        }
        return TunnelPublicDTO.builder()
                .id(t.getId())
                .libelle(t.getLibelle())
                .type(t.getType() != null ? t.getType().name() : null)
                .longueur(t.getLongueur())
                .departement(t.getDepartement())
                .dateDerniereVisite(t.getDateDerniereVisite() != null ? t.getDateDerniereVisite().toString() : null)
                .statutConformite(statutConformite)
                .build();
    }
}
