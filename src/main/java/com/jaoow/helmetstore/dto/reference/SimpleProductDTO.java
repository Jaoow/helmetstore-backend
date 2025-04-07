package com.jaoow.helmetstore.dto.reference;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleProductDTO {
    private Long id;
    private String model;
    private String color;
    private String imgUrl;
}