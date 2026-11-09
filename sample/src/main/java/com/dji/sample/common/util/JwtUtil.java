package com.dji.sample.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.dji.sample.common.model.CustomClaim;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class JwtUtil {

    private static String issuer;

    private static String subject;

    private static long age;

    private static String secret;

    public static Algorithm algorithm;

    @Value("${jwt.issuer: DJI}")
    private void setIssuer(String issuer) {
        JwtUtil.issuer = issuer;
    }

    @Value("${jwt.subject: CloudApiSample}")
    private void setSubject(String subject) {
        JwtUtil.subject = subject;
    }

    @Value("${jwt.age: 86400}")
    private void setAge(long age) {
        JwtUtil.age = age * 1000;
    }

    @Value("${jwt.secret: CloudApiSample}")
    private void setSecret(String secret) {
        JwtUtil.secret = secret;
        setAlgorithm();
    }

    private void setAlgorithm() {
        JwtUtil.algorithm = Algorithm.HMAC256(secret);
    }

    private JwtUtil() {

    }

    /**
     * Create a token based on custom information.
     * @param claims custom information
     * @return token
     */
    public static String createToken(Map<String, ?> claims) {
        return JwtUtil.createToken(claims, age, algorithm, subject, issuer);
    }

    public static String createToken(Map<String, ?> claims, long age) {
        return JwtUtil.createToken(claims, age*1000, algorithm, subject, issuer);
    }

    /**
     *
     * @param claims
     * @param age       unit: s
     * @param algorithm
     * @param subject
     * @param issuer
     * @return
     */
    public static String createToken(Map<String, ?> claims, Long age, Algorithm algorithm, String subject, String issuer) {
        if (Objects.isNull(algorithm)) {
            throw new IllegalArgumentException();
        }

        Date now = new Date();
        JWTCreator.Builder builder = JWT.create();
        // Add custom information to the token's payload segment.
        claims.forEach((k, v) -> {
            if (Objects.nonNull(v.getClass().getClassLoader())) {
                log.error("claim can't be set to a custom object.");
                return;
            }
            if (v instanceof Map) {
                builder.withClaim(k, (Map) v);
            } else if (v instanceof List) {
                builder.withClaim(k, (List) v);
            } else {
                builder.withClaim(k, String.valueOf(v));
            }
        });

        if (StringUtils.hasText(subject)) {
            builder.withSubject(subject);
        }

        if (StringUtils.hasText(issuer)) {
            builder.withIssuer(issuer);
        }

        if (Objects.nonNull(age)) {
            builder.withExpiresAt(new Date(now.getTime() + age));
        }

        String token = builder
                .withIssuedAt(now)
                .withNotBefore(now)
                .sign(algorithm);
        log.debug("token created. " + token);
        return token;
    }

    public static String createToken(String secret, String subject, Long age) throws JsonProcessingException {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> headerClaims = new LinkedHashMap<>();
        headerClaims.put("alg", "HS256");
        headerClaims.put("typ", "JWT");
        Map<String, Object> payloadClaims = new LinkedHashMap<>();
        payloadClaims.put("sub", subject);
        payloadClaims.put("exp", age);
        String headerJson = mapper.writeValueAsString(headerClaims);
        String payloadJson = mapper.writeValueAsString(payloadClaims);
        String header = Base64.encodeBase64URLSafeString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.encodeBase64URLSafeString(payloadJson.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = algorithm.sign(header.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.encodeBase64URLSafeString(signatureBytes);
        String token = String.format("%s.%s.%s", header, payload, signature);
        log.debug("permanent token created. " + token);
        return token;
    }

    /**
     * Verify that the token is valid.
     * @param token
     * @return
     * @throws TokenExpiredException
     */
    public static DecodedJWT verifyToken(String token) {
        return JWT.require(algorithm).build().verify(token);
    }

    /**
     * Parses the custom information in the token into a CustomClaim object.
     * @param token
     * @return custom claim
     */
    public static Optional<CustomClaim> parseToken(String token) {
        DecodedJWT jwt;
        try {
            jwt = verifyToken(token);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
        return Optional.of(new CustomClaim(jwt.getClaims()));
    }
}
