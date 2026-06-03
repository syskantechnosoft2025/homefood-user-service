package com.homefood.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtpRequest {

    @NotBlank
    private String phone;

    @NotBlank
    @Size(min = 6, max = 6)
    private String otp;
}
