package org.msvcompraentradasol.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CompraRequestDTO {

    @NotBlank
    @Size(max = 100)
    private String claveIdempotencia;

    @NotNull
    @Positive
    private Long idCliente;

    @NotNull
    @Positive
    private Long idEvento;

    @NotNull
    @Positive
    private Long idSector;

    private List<@NotNull @Positive Long> idsAsientos;

    @Positive
    private Integer cantidadEntradas;

    @NotBlank
    private String medioDePago;

    public String getClaveIdempotencia() {return claveIdempotencia;}
    public void setClaveIdempotencia(String claveIdempotencia) {this.claveIdempotencia = claveIdempotencia;}

    public Long getIdCliente() {return idCliente;}
    public void setIdCliente(Long idCliente) {this.idCliente = idCliente;}

    public Long getIdEvento() {return idEvento;}
    public void setIdEvento(Long idEvento) {this.idEvento = idEvento;}

    public Long getIdSector() {return idSector;}
    public void setIdSector(Long idSector) {this.idSector = idSector;}

    public List<Long> getIdsAsientos() {return idsAsientos;}
    public void setIdsAsientos(List<Long> idsAsientos) {this.idsAsientos = idsAsientos;}

    public Integer getCantidadEntradas() {return cantidadEntradas;}
    public void setCantidadEntradas(Integer cantidadEntradas) {this.cantidadEntradas = cantidadEntradas;}

    public String getMedioDePago() {return medioDePago;}
    public void setMedioDePago(String medioDePago) {this.medioDePago = medioDePago;}
}
