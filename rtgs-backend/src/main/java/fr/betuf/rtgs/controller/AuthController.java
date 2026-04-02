package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.AuthResponseDTO;
import fr.betuf.rtgs.dto.LoginRequestDTO;
import fr.betuf.rtgs.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        if (userId != null) authService.logout(userId);
        return ResponseEntity.ok().build();
    }
}
