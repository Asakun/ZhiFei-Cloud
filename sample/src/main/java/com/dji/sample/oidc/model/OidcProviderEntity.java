package com.dji.sample.oidc.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

/**
 * OIDC provider (OpenID Provider) configuration, stored in the database.
 *
 * <p>Each record describes one provider the sample can act as a Relying Party against.
 * The {@code providerId} also appears in the login URL path
 * ({@code /manage/api/v1/login/oidc/{provider}}).
 *
 * @author dyw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "oidc_provider")
@Entity
@Table(name = "oidc_provider")
public class OidcProviderEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "provider_id", nullable = false, length = 64, unique = true)
    @Comment("Provider id, also used in the login URL path")
    @TableField(value = "provider_id")
    private String providerId;

    @Column(name = "client_id", nullable = false, length = 128)
    @Comment("The client id registered on the provider")
    @TableField(value = "client_id")
    private String clientId;

    @Column(name = "client_secret", nullable = false, length = 256)
    @Comment("The client secret registered on the provider")
    @TableField(value = "client_secret")
    private String clientSecret;

    @Column(name = "issuer", nullable = false, length = 256)
    @Comment("The issuer identifier, used to validate the iss claim")
    @TableField(value = "issuer")
    private String issuer;

    @Column(name = "authorization_uri", nullable = false, length = 512)
    @Comment("The providers authorization endpoint")
    @TableField(value = "authorization_uri")
    private String authorizationUri;

    @Column(name = "token_uri", nullable = false, length = 512)
    @Comment("The providers token endpoint")
    @TableField(value = "token_uri")
    private String tokenUri;

    @Column(name = "redirect_uri", nullable = false, length = 512)
    @Comment("The callback address registered on the provider")
    @TableField(value = "redirect_uri")
    private String redirectUri;

    @Column(name = "jwk_set_uri", length = 512)
    @Comment("The providers JWKS endpoint, used to fetch the public key dynamically")
    @TableField(value = "jwk_set_uri")
    private String jwkSetUri;

    @Column(name = "public_key", length = 65536)
    @Comment("The providers RSA public key in PEM format ignored when jwk_set_uri is set")
    @TableField(value = "public_key")
    private String publicKey;

    @Column(name = "scopes", nullable = false, length = 128)
    @ColumnDefault("'openid profile'")
    @Comment("Space separated scopes requested from the provider")
    @TableField(value = "scopes")
    private String scopes;

    @Column(name = "username_claim", nullable = false, length = 64)
    @ColumnDefault("'preferred_username'")
    @Comment("The id_token claim that carries the local username")
    @TableField(value = "username_claim")
    private String usernameClaim;

    @Column(name = "state_ttl", nullable = false)
    @ColumnDefault("120")
    @Comment("The lifetime seconds of the state stored in redis")
    @TableField(value = "state_ttl")
    private Long stateTtl;

    @Column(name = "enable", nullable = false)
    @ColumnDefault("1")
    @Comment("1: enable; 0: disable")
    @TableField(value = "enable")
    private Boolean enable;

    @Column(name = "is_del", nullable = false)
    @ColumnDefault("0")
    @Comment("1: del; 0: not del")
    @TableField(value = "is_del")
    private Boolean isDel;

    @Column(name = "create_time", nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time", nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}
