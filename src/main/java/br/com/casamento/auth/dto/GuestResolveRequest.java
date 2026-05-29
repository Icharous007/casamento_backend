package br.com.casamento.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GuestResolveRequest(@NotBlank String token) {}
