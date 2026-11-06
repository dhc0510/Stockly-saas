package com.stockly.companies;

import jakarta.validation.Validator;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.stockly.companies.CompanyContracts.*;

@Component
public class CompanyValidation {
    private final Validator validator;
    public CompanyValidation(Validator validator) { this.validator = validator; }
    static String trim(String value) { return value == null ? null : value.strip(); }
    static String lower(String value) { return value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
    static String optional(String value) { var text=trim(value); return text == null || text.isEmpty() ? null : text; }
    public CompanyInput normalize(CompanyInput input) {
        if(input == null) throw invalid("company", "Ingresa los datos de la empresa.");
        var id = input.identification() == null ? null : input.identification().replaceAll("[\\s-]", "");
        var result = new CompanyInput(trim(input.commercialName()), trim(input.legalName()), input.identificationType(),
            id, lower(input.contactEmail()), optional(input.phone()), optional(input.address()));
        check(result);
        String pattern = switch(result.identificationType()) {
            case "PHYSICAL" -> "[1-9][0-9]{8}";
            case "LEGAL" -> "[1-9][0-9]{9}";
            case "DIMEX" -> "[1-9][0-9]{10,11}";
            case "NITE" -> "[0-9]{10}";
            default -> throw invalid("identificationType", "Selecciona un tipo de identificación.");
        };
        if(!id.matches(pattern)) throw invalid("identification", "La identificación no corresponde al tipo seleccionado.");
        if(result.phone()!=null && (!result.phone().matches("\\+?[0-9 ()-]+") ||
            !result.phone().replaceAll("[^0-9]", "").matches("[0-9]{8,20}")))
            throw invalid("phone", "Ingresa un teléfono de 8 a 20 dígitos.");
        return result;
    }
    public AdministratorInput normalize(AdministratorInput input) {
        if(input == null) throw invalid("administrator", "Ingresa el administrador inicial.");
        var result = new AdministratorInput(trim(input.fullName()),lower(input.username()),lower(input.email()),input.temporaryPassword());
        check(result); return result;
    }
    public <T> T check(T input) {
        if(input == null) throw invalid("request", "Solicitud incompleta.");
        Map<String,String> fields = new LinkedHashMap<>();
        validator.validate(input).forEach(v -> fields.put(v.getPropertyPath().toString(), "Revisa el formato y la longitud de este campo."));
        if(!fields.isEmpty()) throw new CompanyFailure(400,"VALIDATION","Revisa los campos indicados.",fields);
        return input;
    }
    static CompanyFailure invalid(String field, String message) {
        return new CompanyFailure(400,"VALIDATION",message,Map.of(field,message));
    }
}
