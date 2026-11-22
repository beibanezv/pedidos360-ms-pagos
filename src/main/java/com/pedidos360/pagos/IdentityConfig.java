package com.pedidos360.pagos;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ResponseStatusException;

@Configuration
class IdentityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())).build();
  }

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${CORS_ALLOWED_ORIGINS:http://localhost:4200}") List<String> origins) {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(origins);
    cors.setAllowedMethods(List.of("GET", "POST"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/pagos/**", cors);
    return source;
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${AZURE_TENANT_ID:e5372bf0-c5e3-4286-887c-79069f209c1f}") String tenant,
      @Value("${AZURE_AUDIENCE:api://446c57cb-aba2-4b7a-ab01-6f2e6af7d35c}") String azureAudience,
      @Value("${COGNITO_ISSUER:https://cognito-idp.us-east-1.amazonaws.com/us-east-1_A8lrPsDta}") String cognitoIssuer,
      @Value("${COGNITO_CLIENT_ID:2ba1fjeai3c2i77sb5dpd7j91m}") String cognitoAudience) {
    String azureIssuer = "https://sts.windows.net/" + tenant + "/";
    var decoders = Map.of(
        azureIssuer, decoder(azureIssuer, "https://login.microsoftonline.com/" + tenant + "/discovery/keys",
            azureAudience, false),
        cognitoIssuer, decoder(cognitoIssuer, cognitoIssuer + "/.well-known/jwks.json", cognitoAudience, true));
    return token -> {
      try {
        String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        var selected = issuer == null ? null : decoders.get(issuer);
        if (selected == null) throw new BadJwtException("Emisor no permitido");
        return selected.decode(token);
      } catch (ParseException e) {
        throw new BadJwtException("JWT malformado", e);
      }
    };
  }

  private static JwtDecoder decoder(String issuer, String keys, String audience, boolean cognito) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
        new JwtClaimValidator<Object>("exp", exp -> exp != null),
        new JwtClaimValidator<String>("token_use", use -> !cognito || "id".equals(use))));
    return decoder;
  }

  static String usuario(Jwt jwt) {
    String oid = jwt.getClaimAsString("oid");
    if (oid != null && !oid.isBlank()) return oid;
    String sub = jwt.getSubject();
    if (sub != null && !sub.isBlank()) return sub;
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidad no válida");
  }
}
