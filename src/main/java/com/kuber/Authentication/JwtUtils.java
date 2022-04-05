package com.kuber.Authentication;
import java.util.*;
import java.util.stream.Collectors;

import com.kuber.service.mapper.UserDetailsMapper;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.*;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${kuberbeverages.app.jwtSecret}")
    private String jwtSecret;
    @Value("${kuberbeverages.app.jwtExpirationMs}")
    private int jwtExpirationMs;

    public String generateJwtToken(Authentication authentication) {
        UserDetailsMapper userPrincipal = (UserDetailsMapper) authentication.getPrincipal();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return Jwts.builder()
                .setSubject((userPrincipal.getUsername()))
                 .claim("roles", authorities)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();

    }
    public String getUserNameFromJwtToken(String token)  {

        String[] split_string = token.split("\\.");
        String base64EncodedBody = split_string[1];

        Base64.Decoder decoder = Base64.getDecoder();
        String body = new String(decoder.decode(base64EncodedBody));
        JSONObject jsonObject = new JSONObject(body);
        String userName = jsonObject.get("sub").toString();
        return userName;

    }

    public Collection<? extends GrantedAuthority>  getRolesFromJwtToken(String token)  {

        String[] split_string = token.split("\\.");
        String base64EncodedBody = split_string[1];

        Base64.Decoder decoder = Base64.getDecoder();
        String body = new String(decoder.decode(base64EncodedBody));
        JSONObject jsonObject = new JSONObject(body);
        String rolesString = jsonObject.get("roles").toString();

        final Collection<? extends GrantedAuthority> authorities = !rolesString.isEmpty() ?
                Arrays.stream(jsonObject.get("roles").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()) : new ArrayList<>();

        return authorities;

    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
            return true;
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
