package org.tanzu.mcpclient.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
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
			)
			.logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"));

		return http.build();
	}
}
