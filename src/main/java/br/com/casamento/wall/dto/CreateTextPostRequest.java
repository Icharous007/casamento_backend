package br.com.casamento.wall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTextPostRequest(
        @NotBlank @Size(max = 500) String content
) {}
