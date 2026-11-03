package br.com.casamento.gift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PixSettingsRequest(
        @NotBlank @Size(max = 150) String pixKey,
        @NotBlank @Pattern(regexp = "CPF|CNPJ|EMAIL|PHONE|EVP") String pixKeyType,
        @Size(max = 25) String merchantName,
        @NotBlank @Size(max = 15) String merchantCity
) {}