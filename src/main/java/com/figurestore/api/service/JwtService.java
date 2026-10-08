package com.figurestore.api.service;

import com.figurestore.api.dto.response.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final long expiresIn;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.expires-in-seconds:7200}") long expiresIn) {
        this.encoder = encoder;
        this.expiresIn = expiresIn;
    }

    public TokenResponse issue(Authentication authentication) {
        Instant now = Instant.now();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("figurevault")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiresIn))
                .subject(authentication.getName())
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", expiresIn);
    }
}
