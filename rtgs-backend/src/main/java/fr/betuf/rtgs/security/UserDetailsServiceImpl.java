package fr.betuf.rtgs.security;

import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.entity.enums.UserStatut;
import fr.betuf.rtgs.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé: " + email));

        boolean enabled = utilisateur.getStatut() != UserStatut.SUSPENDU
                && utilisateur.getStatut() != UserStatut.DESACTIVE;

        return new User(
                utilisateur.getEmail(),
                utilisateur.getMotDePasse(),
                enabled,       // enabled
                true,          // accountNonExpired
                true,          // credentialsNonExpired
                enabled,       // accountNonLocked
                List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()))
        );
    }
}
