package com.pokeshop.ventas.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ventas")
@Data
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVenta;

    @Column(nullable = false)
    private Long idUsuario; // ID foráneo del servicio de usuarios

    private LocalDateTime fecha;

    private Double subtotal;
    private Double iva;     // 19%
    private Double total;   // Subtotal + IVA

    private String estado;  // Ej: "COMPLETADA"

    // Relación Uno a Muchos con Detalle
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetalleVenta> detalles = new ArrayList<>();
}
