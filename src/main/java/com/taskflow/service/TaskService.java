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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> search(Long projectId, TaskStatus status) {
        List<Task> tasks;
        if (projectId != null && status != null) {
            tasks = taskRepository.findByProjectIdAndStatus(projectId, status);
        } else if (projectId != null) {
            tasks = taskRepository.findByProjectId(projectId);
        } else if (status != null) {
            tasks = taskRepository.findByStatus(status);
        } else {
            tasks = taskRepository.findAll();
        }
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return taskRepository.findById(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Tarea", id));
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        validateReferences(request.projectId(), request.assigneeId());

        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .status(request.status() != null ? request.status() : TaskStatus.TODO)
                .projectId(request.projectId())
                .assigneeId(request.assigneeId())
                .dueDate(request.dueDate())
                .build();

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Tarea", id));

        validateReferences(request.projectId(), request.assigneeId());

        task.setTitle(request.title());
        task.setDescription(request.description());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        task.setProjectId(request.projectId());
        task.setAssigneeId(request.assigneeId());
        task.setDueDate(request.dueDate());

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatusUpdateRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Tarea", id));

        task.setStatus(request.status());

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Tarea", id);
        }
        taskRepository.deleteById(id);
    }

    private void validateReferences(Long projectId, Long assigneeId) {
        if (!projectRepository.existsById(projectId)) {
            throw ResourceNotFoundException.of("Proyecto", projectId);
        }
        if (assigneeId != null && !userRepository.existsById(assigneeId)) {
            throw ResourceNotFoundException.of("Usuario (assignee)", assigneeId);
        }
    }
}
