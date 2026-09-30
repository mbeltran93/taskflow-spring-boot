package com.taskflow.service;

import com.taskflow.dto.ProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.entity.Project;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void create_conOwnerValido_creaProyecto() {
        ProjectRequest request = new ProjectRequest("Portafolio", "Proyectos de portafolio", 1L);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(7L);
            return p;
        });

        ProjectResponse response = projectService.create(request);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Portafolio");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void create_conOwnerInexistente_lanzaNotFound() {
        ProjectRequest request = new ProjectRequest("Portafolio", "desc", 99L);
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void delete_inexistente_lanzaNotFound() {
        when(projectRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> projectService.delete(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_existente_devuelveProyecto() {
        Project project = Project.builder().id(3L).name("X").ownerId(1L).build();
        when(projectRepository.findById(3L)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.findById(3L);

        assertThat(response.id()).isEqualTo(3L);
    }
}
