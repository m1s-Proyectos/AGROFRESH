package com.agrofresh.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundUsesSafeMessageAndNotFoundStatus() {
        ModelAndView response = handler.handleResourceNotFound(
                new ResourceNotFoundException("detalle interno que no debe mostrarse"));

        assertThat(response.getViewName()).isEqualTo("error/error");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getModel()).containsEntry("status", 404);
        assertThat(response.getModel().get("errorMessage"))
                .isEqualTo("El recurso solicitado no existe o ya no está disponible.");
        assertThat(response.getModel().toString()).doesNotContain("detalle interno");
    }

    @Test
    void businessRuleFailureUsesBadRequestAndDoesNotExposeExceptionDetails() {
        ModelAndView response = handler.handleBusinessException(
                new BusinessException("información interna de la regla"));

        assertThat(response.getViewName()).isEqualTo("error/error");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getModel()).containsEntry("status", 400);
        assertThat(response.getModel().toString()).doesNotContain("información interna");
    }
}
