package com.taskflow.service;

import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.LoginResponse;
import com.taskflow.entity.User;
import com.taskflow.exception.InvalidCredentialsException;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_conCredencialesValidas_devuelveToken() {
        User user = User.builder()
                .id(1L)
                .name("Ana")
                .email("ana@taskflow.dev")
                .passwordHash("hashed")
                .build();

        when(userRepository.findByEmail("ana@taskflow.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateToken("ana@taskflow.dev", 1L)).thenReturn("fake-jwt-token");

        LoginResponse response = authService.login(new LoginRequest("ana@taskflow.dev", "secret123"));

        assertThat(response.token()).isEqualTo("fake-jwt-token");
    }

    @Test
    void login_conEmailInexistente_lanzaInvalidCredentials() {
        when(userRepository.findByEmail("nadie@taskflow.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@taskflow.dev", "secret123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_conPasswordIncorrecta_lanzaInvalidCredentials() {
        User user = User.builder()
                .id(1L)
                .name("Ana")
                .email("ana@taskflow.dev")
                .passwordHash("hashed")
                .build();

        when(userRepository.findByEmail("ana@taskflow.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mala-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@taskflow.dev", "mala-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
