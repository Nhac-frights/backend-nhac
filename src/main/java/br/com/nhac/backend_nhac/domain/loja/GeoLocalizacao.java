package br.com.nhac.backend_nhac.domain.loja;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocalizacao {

    @Column(name = "geo_lat", columnDefinition = "DECIMAL(10,8)")
    @Schema(description = "Latitude exata da loja no GPS",example="-27.6672")
    private double geoLat;

    @Column(name = "geo_lng", columnDefinition = "DECIMAL(11,8)")
    @Schema(description = "Longitude exata da loja no GPS", example = "-48.2233")
    private double geoLng;

    @Column(name = "geo_geohash")
    @Schema(description = "Hash geográfico para buscas de raio de entrega mais rápidas", example = "6fz2h3k")
    private String geoHash;
}
