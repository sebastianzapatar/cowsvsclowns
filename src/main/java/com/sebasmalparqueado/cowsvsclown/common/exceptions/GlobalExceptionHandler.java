package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse>
    handleResourceNotFoundException(ResourceNotFoundException e) {
        ErrorResponse err=new ErrorResponse(e.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now());
        return ResponseEntity.
                status(HttpStatus.NOT_FOUND).
                body(err);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse>
    handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String,String> errors=new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error)->{
            errors.putIfAbsent(error.getDefaultMessage(),error.getDefaultMessage());
        });
        ErrorResponse err=new ErrorResponse("Not valid data",
                HttpStatus.BAD_REQUEST.value(), LocalDateTime.now()
        ,errors);
        return ResponseEntity.
                status(HttpStatus.BAD_REQUEST).
                body(err);
    }
}
