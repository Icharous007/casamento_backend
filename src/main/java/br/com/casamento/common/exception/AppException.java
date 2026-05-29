package br.com.casamento.common.exception;

import jakarta.ws.rs.core.Response;

public class AppException extends RuntimeException {

    private final String code;
    private final int status;

    public AppException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    // ── Factory methods ─────────────────────────────────────────────────────

    public static AppException notFound(String message) {
        return new AppException("NOT_FOUND", message, Response.Status.NOT_FOUND.getStatusCode());
    }

    public static AppException accessDenied() {
        return new AppException("ACCESS_DENIED", "Credenciais inválidas", Response.Status.UNAUTHORIZED.getStatusCode());
    }

    public static AppException accessDenied(String message) {
        return new AppException("ACCESS_DENIED", message, Response.Status.FORBIDDEN.getStatusCode());
    }

    public static AppException tokenInvalid() {
        return new AppException("TOKEN_INVALID", "Token de acesso inválido", Response.Status.UNAUTHORIZED.getStatusCode());
    }

    public static AppException tokenRevoked() {
        return new AppException("TOKEN_REVOKED", "Token de acesso revogado", 410);
    }

    public static AppException rsvpDeadlineExpired() {
        return new AppException("RSVP_DEADLINE_EXPIRED", "Prazo de confirmação encerrado", Response.Status.CONFLICT.getStatusCode());
    }

    public static AppException giftAlreadyPurchased() {
        return new AppException("GIFT_ALREADY_PURCHASED", "Este presente já foi escolhido por outro convidado", Response.Status.CONFLICT.getStatusCode());
    }

    public static AppException conflict(String code, String message) {
        return new AppException(code, message, Response.Status.CONFLICT.getStatusCode());
    }

    public static AppException badRequest(String code, String message) {
        return new AppException(code, message, Response.Status.BAD_REQUEST.getStatusCode());
    }
}
