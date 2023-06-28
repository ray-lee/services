package org.collectionspace.authentication.spring;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.collectionspace.authentication.CSpaceUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@EnableWebSecurity
public class CSpaceSecurityConfig {

	@Value("${cors.allowed.origins:}")
	private List<String> corsAllowedOrigins ;

	@Value("${default.access.token.ttl.minutes:720}")
	private Long defaultAccessTokenTimeToLiveMinutes;

	@Value("${ui.redirect.uris:}")
	private List<String> uiRedirectUris;

	@Bean
	public JdbcOperations jdbcOperations(DataSource cspaceDataSource) {
		return new JdbcTemplate(cspaceDataSource);
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder().build();
	}

	@Bean
	public OAuth2AuthorizationService authorizationService(JdbcOperations jdbcOperations, RegisteredClientRepository registeredClientRepository) {
		return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

		return http
			.exceptionHandling(new Customizer<ExceptionHandlingConfigurer<HttpSecurity>>() {
				@Override
				public void customize(ExceptionHandlingConfigurer<HttpSecurity> configurer) {
					configurer.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
				}
			})
			.cors(new Customizer<CorsConfigurer<HttpSecurity>>() {
				@Override
				public void customize(CorsConfigurer<HttpSecurity> configurer) {
					configurer.configurationSource(new CorsConfigurationSource() {
						@Override
						@Nullable
						public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
							CorsConfiguration configuration = new CorsConfiguration();

							configuration.setAllowedOrigins(corsAllowedOrigins);
							configuration.setMaxAge(Duration.ofHours(24));
							configuration.setAllowCredentials(true);

							configuration.setAllowedMethods(Arrays.asList(
								"POST",
								"GET"
							));

							return configuration;
						}
					});
				}
			})
			.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, final AuthenticationManager authenticationManager, final UserDetailsService userDetailsService) throws Exception {
		return http
			.authorizeHttpRequests(new Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>() {
				@Override
				public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry configurer) {
					configurer
						// Exclude the resource path to public items' content from AuthN and AuthZ. Lets us publish resources with anonymous access.
						.requestMatchers("/publicitems/*/*/content").permitAll()

						// Exclude the resource path to handle an account password reset request from AuthN and AuthZ. Lets us process password resets anonymous access.
						.requestMatchers("/accounts/requestpasswordreset").permitAll()

						// Exclude the resource path to account process a password resets from AuthN and AuthZ. Lets us process password resets anonymous access.
						.requestMatchers("/accounts/processpasswordreset").permitAll()

						// Exclude the resource path to request system info.
						.requestMatchers("/systeminfo").permitAll()

						// Handle CORS (preflight OPTIONS requests must be anonymous).
						.requestMatchers(HttpMethod.OPTIONS).permitAll()

						// All other paths must be authenticated.
						.anyRequest().fullyAuthenticated();
				}
			})
			.oauth2ResourceServer(new Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>>() {
				@Override
				public void customize(OAuth2ResourceServerConfigurer<HttpSecurity> configurer) {
					configurer.jwt(new Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer>() {
						@Override
						public void customize(OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer jwtConfigurer) {
							// By default, authentication results in a JwtAuthenticationToken, where the principal is a Jwt instance.
							// We want the principal to be a CSpaceUser instance, so that authentication functions will continue to
							// work as they do with basic auth and session auth. This conversion code is based on comments in
							// https://github.com/spring-projects/spring-security/issues/7834

							jwtConfigurer.jwtAuthenticationConverter(new Converter<Jwt,CSpaceJwtAuthenticationToken>() {
								@Override
								@Nullable
								public CSpaceJwtAuthenticationToken convert(Jwt jwt) {
									CSpaceUser user = null;
									String username = (String) jwt.getClaims().get("sub");

									try {
										user = (CSpaceUser) userDetailsService.loadUserByUsername(username);
									} catch (UsernameNotFoundException e) {
										user = null;
									}

									return new CSpaceJwtAuthenticationToken(jwt, user);
								}
							});
						}
					});
				}
			})
			.httpBasic(new Customizer<HttpBasicConfigurer<HttpSecurity>>() {
				@Override
				public void customize(HttpBasicConfigurer<HttpSecurity> configurer) {}
			})
			.formLogin(new Customizer<FormLoginConfigurer<HttpSecurity>>() {
				@Override
				public void customize(FormLoginConfigurer<HttpSecurity> configurer) {
					configurer
						.defaultSuccessUrl("/accounts/0/accountperms");
				}
			})
			.logout(new Customizer<LogoutConfigurer<HttpSecurity>>() {
				@Override
				public void customize(LogoutConfigurer<HttpSecurity> configurer) {}
			})
			.csrf(new Customizer<CsrfConfigurer<HttpSecurity>>() {
				@Override
				public void customize(CsrfConfigurer<HttpSecurity> configurer) {
					configurer.requireCsrfProtectionMatcher(new OrRequestMatcher(
						new AntPathRequestMatcher("/login", "POST")
					));
				}
			})
			.anonymous(new Customizer<AnonymousConfigurer<HttpSecurity>>() {
				@Override
				public void customize(AnonymousConfigurer<HttpSecurity> configurer) {
					configurer.principal("anonymous");
				}
			})
			.cors(new Customizer<CorsConfigurer<HttpSecurity>>() {
				@Override
				public void customize(CorsConfigurer<HttpSecurity> configurer) {
					configurer.configurationSource(new CorsConfigurationSource() {
						@Override
						@Nullable
						public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
							CorsConfiguration configuration = new CorsConfiguration();

							configuration.setAllowedOrigins(corsAllowedOrigins);
							configuration.setMaxAge(Duration.ofHours(24));
							configuration.setAllowCredentials(true);

							configuration.setAllowedHeaders(Arrays.asList(
								"Authorization",
								"Content-Type"
							));

							configuration.setAllowedMethods(Arrays.asList(
								"POST",
								"GET",
								"PUT",
								"DELETE"
							));

							configuration.setExposedHeaders(Arrays.asList(
								"Location",
								"Content-Disposition",
								"Www-Authenticate"
							));

							return configuration;
						}
					});
				}
			})
			// Insert the username from the security context into a request attribute for logging.
			.addFilterAfter(new CSpaceUserAttributeFilter(), SecurityContextHolderFilter.class)
			.build();
	}

	@Bean
	public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
		RegisteredClient.Builder registeredClientBuilder = RegisteredClient
			.withId("f310dec6-3c0f-45e4-969a-dd096db135b6")
			.clientId("cspace-ui")
			.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)

			// Spring does not allow refresh token grants for public clients (those with
			// ClientAuthenticationMethod.NONE).
			// .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)

			// The scope attribute below is a meaningless placeholder. In the future we may want to use it to limit
      // the permissions of particular clients. The CSpace UI client has the full permissions of the user on
      // whose behalf it is acting.
			.scope("cspace.full")
			.clientSettings(
				ClientSettings.builder()
					.requireAuthorizationConsent(false)
					.build()
			)
			.tokenSettings(
				TokenSettings.builder()
					.accessTokenTimeToLive(Duration.ofMinutes(defaultAccessTokenTimeToLiveMinutes))
					.build()
			);

		// TODO: Auto-generate redirect uris for enabled tenants.
		// e.g. "/${tenant}/login"
		for (String redirectUri : uiRedirectUris) {
			registeredClientBuilder.redirectUri(redirectUri);
		}

		JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcOperations);
		RegisteredClient registeredClient = registeredClientBuilder.build();

		registeredClientRepository.save(registeredClient);

		return registeredClientRepository;
	}

	private static KeyPair generateRsaKey() {
    KeyPair keyPair;

		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(2048);
			keyPair = keyPairGenerator.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

		return keyPair;
  }

	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		KeyPair keyPair = generateRsaKey();

		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();

		JWKSet jwkSet = new JWKSet(rsaKey);

		return new ImmutableJWKSet<>(jwkSet);
	}

	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}
}
