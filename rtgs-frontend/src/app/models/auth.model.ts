export interface LoginRequestDTO {
  email: string;
  motDePasse: string;
}

export interface AuthResponseDTO {
  token: string;
  role: string;
  nomComplet: string;
  userId: number;
}
