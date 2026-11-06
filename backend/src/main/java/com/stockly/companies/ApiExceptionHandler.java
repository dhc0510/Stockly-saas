package com.stockly.companies;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.*;
import org.springframework.security.access.AccessDeniedException;
import com.stockly.identity.IdentityFailure;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String code,String message,Map<String,String> fieldErrors,String requestId) {}
    private ResponseEntity<ApiError> error(int status,String code,String message,Map<String,String> fields) {
        return ResponseEntity.status(status).body(new ApiError(code,message,fields,UUID.randomUUID().toString()));
    }
    @ExceptionHandler(CompanyFailure.class) ResponseEntity<ApiError> failure(CompanyFailure ex) { return error(ex.status,ex.code,ex.getMessage(),ex.fields); }
    @ExceptionHandler(IdentityFailure.class) ResponseEntity<ApiError> identityFailure(IdentityFailure ex) { return error(ex.status,ex.code,ex.getMessage(),ex.fields); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> duplicate(DataIntegrityViolationException ex) {
        for(Throwable cause=ex;cause!=null;cause=cause.getCause()) {
            if(cause instanceof java.sql.SQLException sql && sql.getErrorCode()==1062)
                return error(409,"CONFLICT","La identificación, el usuario o el correo ya está registrado. Revisa los datos antes de reenviar.",Map.of());
        }
        return error(409,"DEPENDENCIES","Existen datos asociados que requieren revisión antes de completar la operación.",Map.of());
    }
    @ExceptionHandler({OptimisticLockingFailureException.class,PessimisticLockingFailureException.class}) ResponseEntity<ApiError> concurrent() {
        return error(409,"STALE_VERSION","La empresa cambió. Actualiza e intenta de nuevo.",Map.of());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MethodArgumentNotValidException.class}) ResponseEntity<ApiError> invalid() {
        return error(400,"VALIDATION","La solicitud contiene datos inválidos.",Map.of());
    }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ApiError> denied() { return error(403,"FORBIDDEN","No tienes permiso para esta operación.",Map.of()); }
    @ExceptionHandler(Exception.class) ResponseEntity<ApiError> unexpected() { return error(500,"INTERNAL_ERROR","No se pudo completar la operación. Intenta de nuevo.",Map.of()); }
}
