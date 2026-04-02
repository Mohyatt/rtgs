package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.AuthResponseDTO;
import fr.betuf.rtgs.dto.LoginRequestDTO;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.repository.UtilisateurRepository;
import fr.betuf.rtgs.security.JwtUtil;
import fr.betuf.rtgs.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuditLogService auditLogService;

    public AuthResponseDTO login(LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getMotDePasse())
        );

        Utilisateur utilisateur = utilisateurRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtUtil.generateToken(userDetails, utilisateur.getId(),
                utilisateur.getRole().name());

        // Audit log
        auditLogService.save(
                "CONNEXION",
                utilisateur.getId(),
                "UTILISATEUR",
                utilisateur,
                utilisateur.getNomComplet() + " (" + utilisateur.getRole() + ") connecté(e)"
        );

        return AuthResponseDTO.builder()
                .token(token)
                .role(utilisateur.getRole().name())
                .nomComplet(utilisateur.getNomComplet())
                .userId(utilisateur.getId())
                .build();
    }

    public void logout(Long userId) {
        utilisateurRepository.findById(userId).ifPresent(u ->
                auditLogService.save(
                        "DECONNEXION",
                        userId,
                        "UTILISATEUR",
                        u,
                        u.getNomComplet() + " (" + u.getRole() + ") déconnecté(e)"
                )
        );
    }
}
