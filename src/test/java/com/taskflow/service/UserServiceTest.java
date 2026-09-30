package com.taskflow.service;

import com.taskflow.dto.CreateUserRequest;
import com.taskflow.dto.UserResponse;
import com.taskflow.entity.User;
import com.taskflow.exception.DuplicateResourceException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void findById_existente_devuelveUsuario() {
        User user = User.builder().id(5L).name("Bruno").email("bruno@taskflow.dev").passwordHash("x").build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        UserResponse response = userService.findById(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo("bruno@taskflow.dev");
    }

    @Test
    void findById_inexistente_lanzaNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_conEmailNuevo_hasheaPasswordYGuarda() {
        CreateUserRequest request = new CreateUserRequest("Carla", "carla@taskflow.dev", "password123");
        when(userRepository.existsByEmail("carla@taskflow.dev")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash-seguro");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponse response = userService.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash-seguro");
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("carla@taskflow.dev");
    }

    @Test
    void create_conEmailDuplicado_lanzaConflict() {
        CreateUserRequest request = new CreateUserRequest("Carla", "carla@taskflow.dev", "password123");
        when(userRepository.existsByEmail("carla@taskflow.dev")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }
}
