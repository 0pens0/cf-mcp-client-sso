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
					"/",
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
					"/oauth2/**",
					"/welcome",
					"/error"
				).permitAll()
				.requestMatchers("/api/**", "/chat/**", "/document/**", "/mcp/**", "/memory/**", "/prompt/**", "/vectorstore/**", "/metrics/**").authenticated()
				.anyRequest().authenticated()
			)
			.oauth2Login(oauth -> oauth
				// render static login page; its button will choose provider
				.loginPage("/login.html")
				.defaultSuccessUrl("/welcome", true)
			)
			.logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"));

		return http.build();
	}
}