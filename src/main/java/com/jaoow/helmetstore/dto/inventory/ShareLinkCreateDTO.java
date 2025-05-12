package com.jaoow.helmetstore.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkCreateDTO {

    @Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Token deve conter apenas letras, números, hífens e sublinhados")
    @NotBlank(message = "Token não pode ser vazio")
    private String token;

}
