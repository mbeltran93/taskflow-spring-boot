package com.taskflow.dto;

import com.taskflow.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(
        @NotNull TaskStatus status
) {
}
