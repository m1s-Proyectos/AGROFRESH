package com.agrofresh.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleResourceNotFound(ResourceNotFoundException exception) {
        LOGGER.warn("No se encontró el recurso solicitado.");
        return errorView(
                HttpStatus.NOT_FOUND,
                "No encontramos lo que buscas",
                "El recurso solicitado no existe o ya no está disponible.");
    }

    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException exception) {
        LOGGER.warn("Una solicitud no pudo completarse por una regla de negocio.");
        return errorView(
                HttpStatus.BAD_REQUEST,
                "No se pudo completar la solicitud",
                "Revisa la información e inténtalo nuevamente.");
    }

    private ModelAndView errorView(HttpStatus status, String title, String message) {
        ModelAndView modelAndView = new ModelAndView("error/error");
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("errorTitle", title);
        modelAndView.addObject("errorMessage", message);
        return modelAndView;
    }
}
