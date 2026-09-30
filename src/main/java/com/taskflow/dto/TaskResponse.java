package com.taskflow.dto;

import com.taskflow.entity.Task;
import com.taskflow.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Long projectId,
        Long assigneeId,
        LocalDate dueDate,
        Instant createdAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getProjectId(),
                task.getAssigneeId(),
                task.getDueDate(),
                task.getCreatedAt()
        );
    }
}
