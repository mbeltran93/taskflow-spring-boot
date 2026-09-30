package com.taskflow.service;

import com.taskflow.dto.ProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.entity.Project;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return projectRepository.findById(id)
                .map(ProjectResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Proyecto", id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        if (!userRepository.existsById(request.ownerId())) {
            throw new ResourceNotFoundException("Usuario (owner) no encontrado con id " + request.ownerId());
        }

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .ownerId(request.ownerId())
                .build();

        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Proyecto", id));

        if (!userRepository.existsById(request.ownerId())) {
            throw new ResourceNotFoundException("Usuario (owner) no encontrado con id " + request.ownerId());
        }

        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwnerId(request.ownerId());

        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Proyecto", id);
        }
        projectRepository.deleteById(id);
    }
}
