package com.jobportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.jobportal.services.GitHubOAuth2UserService;
import com.jobportal.services.OAuthLoginFailureHandler;
import com.jobportal.services.OAuthLoginSuccessHandler;

@Configuration
public class WebSecurityConfig {

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GitHubOAuth2UserService githubOAuth2UserService,
            OAuthLoginSuccessHandler successHandler,
            OAuthLoginFailureHandler failureHandler) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(githubOAuth2UserService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }
}
