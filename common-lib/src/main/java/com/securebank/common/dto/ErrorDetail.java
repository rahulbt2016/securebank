package com.securebank.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    private int status;
    private String code;
    private String message;

    /** Field-level validation errors: { "email": "must be a valid email", "phone": "must not be blank" } */
    private Map<String, String> fieldErrors;
}
