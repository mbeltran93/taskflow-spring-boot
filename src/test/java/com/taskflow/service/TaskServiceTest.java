package com.taskflow.service;

import com.taskflow.dto.TaskRequest;
import com.taskflow.dto.TaskResponse;
import com.taskflow.dto.TaskStatusUpdateRequest;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskStatus;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void create_conProjectValido_creaTareaConStatusDefault() {
        TaskRequest request = new TaskRequest("Diseñar API", "detalle", null, 1L, null, null);
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(42L);
            return t;
        });

        TaskResponse response = taskService.create(request);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void create_conProyectoInexistente_lanzaNotFound() {
        TaskRequest request = new TaskRequest("Titulo", "detalle", null, 404L, null, null);
        when(projectRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(taskRepository, never()).save(any());
    }

    @Test
    void create_conAssigneeInexistente_lanzaNotFound() {
        TaskRequest request = new TaskRequest("Titulo", "detalle", null, 1L, 55L, null);
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(userRepository.existsById(55L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_actualizaSoloElStatus() {
        Task task = Task.builder().id(1L).title("T").projectId(1L).status(TaskStatus.TODO).build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = taskService.updateStatus(1L, new TaskStatusUpdateRequest(TaskStatus.DONE));

        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void search_filtraPorProjectIdYStatus() {
        when(taskRepository.findByProjectIdAndStatus(1L, TaskStatus.DONE))
                .thenReturn(List.of(Task.builder().id(1L).title("T").projectId(1L).status(TaskStatus.DONE).build()));

        List<TaskResponse> results = taskService.search(1L, TaskStatus.DONE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(TaskStatus.DONE);
        verify(taskRepository).findByProjectIdAndStatus(1L, TaskStatus.DONE);
    }

    @Test
    void delete_inexistente_lanzaNotFound() {
        when(taskRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
