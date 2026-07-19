package org.tanzu.mcpclient.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the {@code k8s} profile.
 * <p>
 * Default {@code app.security.demo-mode=true} permits API use without CF-SSO so chat
 * and document upload can be exercised on VKS. Set {@code app.security.demo-mode=false}
 * and configure OAuth2 client registrations to require login.
 */
@Configuration
@EnableWebSecurity
@Profile("k8s")
public class K8sSecurityConfig {

    @Value("${app.security.demo-mode:true}")
    private boolean demoMode;

    @Bean
    SecurityFilterChain k8sSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());

        if (demoMode) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
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
                            .anyRequest().authenticated())
                    .oauth2Login(oauth -> oauth
                            .loginPage("/login.html")
                            .defaultSuccessUrl("/welcome", true))
                    .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"))
                    .httpBasic(Customizer.withDefaults());
        }

        return http.build();
    }
}
