package br.com.casamento.common.exception;

import br.com.casamento.common.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    void shouldMapAppExceptionWithGivenStatusAndCode() {
        Response response = mapper.toResponse(AppException.badRequest("PHONE_DUPLICATE", "Telefone ja usado"));

        assertEquals(400, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals("PHONE_DUPLICATE", body.code);
        assertEquals("Telefone ja usado", body.message);
    }

    @Test
    void shouldMapConstraintViolationTo422ValidationError() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Payload>> violations = validator.validate(new Payload());

        Response response = mapper.toResponse(new ConstraintViolationException(violations));

        assertEquals(422, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals("VALIDATION_ERROR", body.code);
        assertNotNull(body.message);
        assertTrue(body.message.startsWith("Dados"));
        assertNotNull(body.details);
        assertTrue(body.details.containsKey("violations"));
    }

    @Test
    void shouldMapWebApplicationExceptionUsingHttpCodeMap() {
        Response response = mapper.toResponse(new NotFoundException("nao encontrado"));

        assertEquals(404, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals("NOT_FOUND", body.code);
    }

    @Test
    void shouldHideInternalExceptionMessageFromClients() {
        Response response = mapper.toResponse(new RuntimeException("segredo interno"));

        assertEquals(500, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals("INTERNAL_ERROR", body.code);
        assertEquals("Erro interno do servidor", body.message);
        assertNotEquals("segredo interno", body.message);
    }

    static class Payload {
        @NotBlank
        String name = "";
    }
}
