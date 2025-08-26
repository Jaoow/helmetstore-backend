package com.jaoow.helmetstore.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogCreateDTO {

    @NotBlank(message = "O nome do catálogo não pode ser vazio")
    @Size(max = 100, message = "O nome do catálogo deve ter no máximo 100 caracteres.")
    private String storeName;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{4,50}$", message = "O token deve conter entre 4 e 50 caracteres alfanuméricos, hífens ou underscores.")
    @NotBlank(message = "Token não pode ser vazio")
    private String token;

}
