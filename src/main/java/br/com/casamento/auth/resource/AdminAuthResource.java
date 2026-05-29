package br.com.casamento.auth.resource;

import br.com.casamento.auth.dto.*;
import br.com.casamento.auth.entity.InternalUser;
import br.com.casamento.auth.filter.RateLimitFilter.RateLimited;
import br.com.casamento.auth.service.*;
import br.com.casamento.common.exception.AppException;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/admin/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminAuthResource {

    @Inject
    PasswordService passwordService;

    @Inject
    JwtService jwtService;

    @Inject
    RefreshTokenService refreshTokenService;

    @Inject
    PasswordResetService passwordResetService;

    @POST
    @Path("/login")
    @RateLimited
    @Transactional
    public Response login(@Valid LoginRequest request) {
        InternalUser user = InternalUser.findByEmail(request.email());

        if (user == null || !user.active || !passwordService.matches(request.password(), user.passwordHash)) {
            throw AppException.accessDenied();
        }

        String accessToken = jwtService.generateToken(user);
        String rawRefreshToken = refreshTokenService.createToken(user);

        return Response.ok(new LoginResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                3600,
                new LoginResponse.UserInfo(
                        user.id.toString(),
                        user.name,
                        user.email,
                        user.role
                )
        )).build();
    }

    @POST
    @Path("/refresh")
    @Transactional
    public Response refresh(@Valid RefreshTokenRequest request) {
        InternalUser user = refreshTokenService.consumeToken(request.refreshToken());

        if (user == null) {
            throw AppException.tokenInvalid();
        }

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = refreshTokenService.createToken(user);

        return Response.ok(new LoginResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                3600,
                new LoginResponse.UserInfo(
                        user.id.toString(),
                        user.name,
                        user.email,
                        user.role
                )
        )).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Valid RefreshTokenRequest request) {
        refreshTokenService.revokeByHash(request.refreshToken());
        return Response.noContent().build();
    }

    @POST
    @Path("/forgot-password")
    @RateLimited
    public Response forgotPassword(@Valid ForgotPasswordRequest request) {
        // Silently processes — no user enumeration
        passwordResetService.requestReset(request.email());
        return Response.accepted().build();
    }

    @POST
    @Path("/reset-password")
    public Response resetPassword(@Valid ResetPasswordRequest request) {
        String newHash = passwordService.hash(request.newPassword());
        boolean ok = passwordResetService.resetPassword(request.token(), newHash);

        if (!ok) {
            throw AppException.badRequest("TOKEN_INVALID_OR_EXPIRED", "Token inválido ou expirado.");
        }

        return Response.noContent().build();
    }
}
