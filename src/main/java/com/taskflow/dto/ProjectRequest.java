package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @NotNull Long ownerId
) {
}
