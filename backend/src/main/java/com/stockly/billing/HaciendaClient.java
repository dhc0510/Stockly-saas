package com.stockly.billing;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API de recepción de Hacienda (comprobantes-electronicos-api v1): token OIDC, POST /recepcion y GET /recepcion/{clave}.
 * Nunca registra credenciales, tokens ni XML completos.
 */
@Component
public class HaciendaClient {
    public enum Outcome { RECEIVED, ALREADY_RECEIVED, ACCEPTED, REJECTED, PROCESSING, NOT_FOUND, RETRY }
    public record Result(Outcome outcome, String detail, byte[] responseXml) {
        static Result of(Outcome outcome, String detail) { return new Result(outcome, detail, null); }
    }
    public record Credentials(String username, String password) {}
    public record Submission(String key, Instant issuedAt, String issuerType, String issuerNumber,
                             String receiverType, String receiverNumber, byte[] signedXml) {}

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final HaciendaEndpoints endpoints;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();
    private record Token(String value, Instant expires) {}

    HaciendaClient(HaciendaEndpoints endpoints) { this.endpoints = endpoints; }

    /** Pide un token nuevo; se usa al guardar credenciales para probar la conexión. */
    public void verify(String environment, Credentials credentials) {
        tokens.remove(environment + "|" + credentials.username());
        token(environment, credentials);
    }

    public Result submit(String environment, Credentials credentials, Submission submission) {
        var endpoint = configured(environment);
        var body = json.createObjectNode();
        body.put("clave", submission.key());
        body.put("fecha", java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .format(submission.issuedAt().atZone(FiscalNumbers.COSTA_RICA).toOffsetDateTime()));
        body.putObject("emisor").put("tipoIdentificacion", submission.issuerType()).put("numeroIdentificacion", submission.issuerNumber());
        if (submission.receiverNumber() != null)
            body.putObject("receptor").put("tipoIdentificacion", submission.receiverType()).put("numeroIdentificacion", submission.receiverNumber());
        body.put("comprobanteXml", Base64.getEncoder().encodeToString(submission.signedXml()));
        var request = HttpRequest.newBuilder(URI.create(endpoint.apiUrl() + "recepcion")).timeout(TIMEOUT)
            .header("Authorization", "bearer " + token(environment, credentials)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 201 || response.statusCode() == 202) return Result.of(Outcome.RECEIVED, null);
            String cause = response.headers().firstValue("X-Error-Cause").orElse("");
            if (response.statusCode() == 400 && cause.contains("ya fue recibido")) return Result.of(Outcome.ALREADY_RECEIVED, cause);
            if (response.statusCode() == 401) tokens.clear();
            if (response.statusCode() == 400) return Result.of(Outcome.REJECTED, trim(cause.isBlank() ? "Hacienda rechazó el envío." : cause));
            return Result.of(Outcome.RETRY, "Hacienda respondió " + response.statusCode() + ".");
        } catch (java.io.IOException e) {
            return Result.of(Outcome.RETRY, "Hacienda no respondió.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.of(Outcome.RETRY, "Envío interrumpido.");
        }
    }

    public Result status(String environment, Credentials credentials, String key) {
        var endpoint = configured(environment);
        var request = HttpRequest.newBuilder(URI.create(endpoint.apiUrl() + "recepcion/" + key)).timeout(TIMEOUT)
            .header("Authorization", "bearer " + token(environment, credentials)).GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 400 || response.statusCode() == 404) return Result.of(Outcome.NOT_FOUND, null);
            if (response.statusCode() == 401) { tokens.clear(); return Result.of(Outcome.RETRY, "Token vencido."); }
            if (response.statusCode() != 200) return Result.of(Outcome.RETRY, "Hacienda respondió " + response.statusCode() + ".");
            JsonNode node = json.readTree(response.body());
            String state = node.path("ind-estado").asString("");
            byte[] answer = node.hasNonNull("respuesta-xml") ? Base64.getDecoder().decode(node.get("respuesta-xml").asString()) : null;
            return switch (state) {
                case "aceptado" -> new Result(Outcome.ACCEPTED, null, answer);
                case "rechazado" -> new Result(Outcome.REJECTED, FiscalResponse.reason(answer), answer);
                case "recibido", "procesando" -> new Result(Outcome.PROCESSING, null, answer);
                default -> new Result(Outcome.RETRY, "Hacienda informó un error al procesar.", answer);
            };
        } catch (java.io.IOException e) {
            return Result.of(Outcome.RETRY, "Hacienda no respondió.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.of(Outcome.RETRY, "Consulta interrumpida.");
        }
    }

    private String token(String environment, Credentials credentials) {
        String cacheKey = environment + "|" + credentials.username();
        Token cached = tokens.get(cacheKey);
        if (cached != null && cached.expires().isAfter(Instant.now().plusSeconds(30))) return cached.value();
        var endpoint = configured(environment);
        String form = "grant_type=password&client_id=" + url(endpoint.clientId()) + "&username=" + url(credentials.username())
            + "&password=" + url(credentials.password());
        var request = HttpRequest.newBuilder(URI.create(endpoint.tokenUrl())).timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 400 || response.statusCode() == 401)
                throw new BillingFailure(400, "BILLING_CREDENTIALS_INVALID", "Hacienda no aceptó el usuario o la contraseña de API.");
            if (response.statusCode() != 200)
                throw new BillingFailure(503, "BILLING_UNAVAILABLE", "No se pudo conectar con Hacienda. Intenta más tarde.");
            JsonNode node = json.readTree(response.body());
            String value = node.path("access_token").asString("");
            if (value.isEmpty()) throw new BillingFailure(503, "BILLING_UNAVAILABLE", "Hacienda no entregó un token.");
            var token = new Token(value, Instant.now().plusSeconds(Math.max(60, node.path("expires_in").asLong(300))));
            tokens.put(cacheKey, token);
            return token.value();
        } catch (java.io.IOException e) {
            throw new BillingFailure(503, "BILLING_UNAVAILABLE", "No se pudo conectar con Hacienda. Intenta más tarde.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BillingFailure(503, "BILLING_UNAVAILABLE", "Conexión interrumpida.");
        }
    }

    private HaciendaEndpoints.Endpoint configured(String environment) {
        var endpoint = endpoints.of(environment);
        if (!endpoint.complete())
            throw new BillingFailure(503, "BILLING_ENDPOINT_MISSING", "El servidor no tiene configuradas las direcciones de Hacienda para este ambiente.");
        return endpoint;
    }

    private static String url(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String trim(String value) { return value.length() > 1000 ? value.substring(0, 1000) : value; }
}
