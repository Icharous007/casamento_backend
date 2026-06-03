package br.com.casamento.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotBlank @Size(max = 500) String content
) {}
