package com.stockly.identity;

import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.stockly.identity.IdentityContracts.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }
    public record CsrfResponse(String csrfToken,String csrfHeaderName) {}
    public record Message(String message) {}
    @GetMapping("/csrf") CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken(),token.getHeaderName()); }
    @PostMapping("/login") LoginResult login(@RequestBody @Valid LoginRequest input,HttpServletRequest request,HttpServletResponse response) {
        return service.login(input,request,response);
    }
    @PostMapping("/device-challenges/verify") ResponseEntity<SessionUser> verify(@RequestBody @Valid VerifyCodeRequest input,HttpServletRequest request,HttpServletResponse response) {
        service.verifyCode(input,request,response); return ResponseEntity.ok(service.me());
    }
    @GetMapping("/me") SessionUser me() { return service.me(); }
    @PostMapping("/logout") ResponseEntity<Void> logout(HttpServletRequest request,HttpServletResponse response) {
        service.logout(request,response); return ResponseEntity.noContent().build();
    }
    @PostMapping("/password/first-change") SessionUser firstChange(@RequestBody @Valid ChangePasswordRequest input,HttpServletRequest request,HttpServletResponse response) {
        service.firstChange(input.newPassword(),request,response); return service.me();
    }
    @PostMapping("/password-recovery") Message recovery(@RequestBody @Valid RecoveryRequest input) {
        service.requestRecovery(input.email()); return new Message("Si el correo corresponde a una cuenta activa y verificada, recibirás un enlace de recuperación.");
    }
    @PostMapping("/password-recovery/complete") Message completeRecovery(@RequestBody @Valid CompleteRecoveryRequest input) {
        service.completeRecovery(input.token(),input.newPassword()); return new Message("Contraseña actualizada. Inicia sesión con tu nueva contraseña.");
    }
}
