package fr.betuf.rtgs.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String email;
    private String motDePasse;
}
