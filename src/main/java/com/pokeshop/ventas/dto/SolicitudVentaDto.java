package com.pokeshop.ventas.dto;

import lombok.Data;
import java.util.List;

@Data
public class SolicitudVentaDto {
    private Long idUsuario;
    private List<ItemProductoDto> productos;

    @Data
    public static class ItemProductoDto {
        private Long idProducto;
        private Integer cantidad;
    }
}
