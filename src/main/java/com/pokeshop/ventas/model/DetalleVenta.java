package com.pokeshop.ventas.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "detalle_venta")
@Data
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @Column(nullable = false)
    private Long idProducto; // ID foráneo del servicio de productos

    private String nombreProducto; // Guardamos el nombre histórico

    private Integer cantidad;
    private Double precioUnitario; 

    @ManyToOne
    @JoinColumn(name = "id_venta")
    @JsonIgnore // Evita bucles infinitos al convertir a JSON
    private Venta venta;
}
