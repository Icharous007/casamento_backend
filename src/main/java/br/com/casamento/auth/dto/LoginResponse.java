package br.com.casamento.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresIn,
        UserInfo user
) {
    public record UserInfo(String id, String name, String email, String role) {}
}
