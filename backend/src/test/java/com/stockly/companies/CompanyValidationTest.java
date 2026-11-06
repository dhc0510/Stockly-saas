package com.stockly.companies;

import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.stockly.companies.CompanyContracts.*;

class CompanyValidationTest {
    private final CompanyValidation validation=new CompanyValidation(Validation.buildDefaultValidatorFactory().getValidator());
    private CompanyInput company(String type,String id) { return new CompanyInput("El Sol","El Sol S.A.",type,id,"contact@example.com",null,null); }
    @Test void acceptsAllApprovedIdentificationTypesWithoutPadding() {
        assertThat(validation.normalize(company("PHYSICAL","1-1234-5678")).identification()).isEqualTo("112345678");
        assertThat(validation.normalize(company("LEGAL","3-101-765432")).identification()).isEqualTo("3101765432");
        assertThat(validation.normalize(company("DIMEX","12345678901")).identification()).isEqualTo("12345678901");
        assertThat(validation.normalize(company("DIMEX","123456789012")).identification()).isEqualTo("123456789012");
        assertThat(validation.normalize(company("NITE","3120123456")).identification()).isEqualTo("3120123456");
    }
    @Test void rejectsWrongTypeInvalidLengthsAndLeadingZeros() {
        for(var type:new String[]{"PHYSICAL","LEGAL","DIMEX"}) assertThatThrownBy(() -> validation.normalize(company(type,"0123456789"))).isInstanceOf(CompanyFailure.class);
        assertThatThrownBy(() -> validation.normalize(company("OTHER","123"))).isInstanceOf(CompanyFailure.class);
    }
    @Test void preservesPasswordSpacesAndValidatesBoundaries() {
        var a=validation.normalize(new AdministratorInput(" Admin "," USER.name "," TEST@example.com ","  contraseña segura  "));
        assertThat(a.temporaryPassword()).isEqualTo("  contraseña segura  ");
        assertThat(a.username()).isEqualTo("user.name");
        assertThat(a.email()).isEqualTo("test@example.com");
        assertThatThrownBy(() -> validation.normalize(new AdministratorInput("Admin","user","a@example.com","short"))).isInstanceOf(CompanyFailure.class);
        assertThatThrownBy(() -> validation.normalize(new AdministratorInput("Admin","user","a@example.com","a".repeat(129)))).isInstanceOf(CompanyFailure.class);
    }
    @Test void rejectsMissingPayloadAndDoesNotLogPasswordInRecords() {
        assertThatThrownBy(() -> validation.normalize((CompanyInput)null)).isInstanceOf(CompanyFailure.class);
        var a=new AdministratorInput("Admin","user","a@example.com","never print password");
        assertThat(a.toString()).doesNotContain("never print");
        assertThat(new CreateCompany(company("LEGAL","3101765432"),a).toString()).doesNotContain("never print");
    }
}
