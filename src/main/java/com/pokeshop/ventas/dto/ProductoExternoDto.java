package com.pokeshop.ventas.dto;

import lombok.Data;


@Data
public class ProductoExternoDto {
    private Long idProducto;
    private String nombre;
    private Double precio;
    private Integer stock;
}
