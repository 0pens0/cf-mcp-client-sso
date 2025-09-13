package org.tanzu.mcpclient.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	
	@Autowired
	private Environment environment;
	
	@Bean
	public ClientRegistrationRepository clientRegistrationRepository() {
		List<ClientRegistration> registrations = new ArrayList<>();
		
		// Check if CF-SSO is configured
		String cfSsoClientId = environment.getProperty("spring.security.oauth2.client.registration.cf-sso.client-id");
		if (cfSsoClientId != null && !cfSsoClientId.equals("cf-sso-client-id")) {
			// Add CF-SSO registration first (priority)
			registrations.add(ClientRegistration.withRegistrationId("cf-sso")
				.clientId(cfSsoClientId)
				.clientSecret(environment.getProperty("spring.security.oauth2.client.registration.cf-sso.client-secret"))
				.authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("openid", "profile", "email")
				.authorizationUri(environment.getProperty("spring.security.oauth2.client.provider.cf-sso.authorization-uri"))
				.tokenUri(environment.getProperty("spring.security.oauth2.client.provider.cf-sso.token-uri"))
				.userInfoUri(environment.getProperty("spring.security.oauth2.client.provider.cf-sso.user-info-uri"))
				.userNameAttributeName(environment.getProperty("spring.security.oauth2.client.provider.cf-sso.user-name-attribute"))
				.jwkSetUri(environment.getProperty("spring.security.oauth2.client.provider.cf-sso.jwk-set-uri"))
				.clientName("Cloud Foundry SSO")
				.build());
		}
		
		// Add GitHub registration if configured
		String githubClientId = environment.getProperty("spring.security.oauth2.client.registration.github.client-id");
		if (githubClientId != null && !githubClientId.equals("your-github-client-id")) {
			registrations.add(ClientRegistration.withRegistrationId("github")
				.clientId(githubClientId)
				.clientSecret(environment.getProperty("spring.security.oauth2.client.registration.github.client-secret"))
				.authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("user:email", "read:user")
				.authorizationUri("https://github.com/login/oauth/authorize")
				.tokenUri("https://github.com/login/oauth/access_token")
				.userInfoUri("https://api.github.com/user")
				.userNameAttributeName("login")
				.clientName("GitHub")
				.build());
		}
		
		return new InMemoryClientRegistrationRepository(registrations);
	}
	
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/login",
					"/login.html",
					"/static/**",
					"/css/**",
					"/js/**",
					"/images/**",
					"/favicon.ico",
					"/auth/status",
					"/auth/provider",
					"/actuator/health**",
					"/actuator/info",
					"/login**",
					"/oauth2/**"
				).permitAll()
				.requestMatchers("/api/**").authenticated()
				.requestMatchers("/chat/**").authenticated()
				.requestMatchers("/document/**").authenticated()
				.requestMatchers("/memory/**").authenticated()
				.requestMatchers("/mcp/**").authenticated()
				.requestMatchers("/prompt/**").authenticated()
				.requestMatchers("/vectorstore/**").authenticated()
				.requestMatchers("/metrics/**").authenticated()
				.anyRequest().authenticated()
			)
			.oauth2Login(oauth -> oauth
				// render static login page; its button will choose provider
				.loginPage("/login.html")
				.defaultSuccessUrl("/", true)
				// Prioritize CF-SSO for Cloud Foundry deployments
				.clientRegistrationRepository(clientRegistrationRepository())
			)
			.logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"));

		return http.build();
	}
}
