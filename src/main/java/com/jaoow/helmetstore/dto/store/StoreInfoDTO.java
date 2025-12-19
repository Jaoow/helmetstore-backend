package com.jaoow.helmetstore.dto.store;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreInfoDTO {
    
    private Long id;

    @NotBlank(message = "Nome da loja é obrigatório")
    private String name;

    @NotBlank(message = "Endereço é obrigatório")
    private String address;

    @NotBlank(message = "Telefone é obrigatório")
    private String phone;

    private String cnpj;
    
    private String email;
    
    private String website;
}
