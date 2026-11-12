package com.son.leafcare.disease;

import com.son.leafcare.plant.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Disease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Vui lòng nhập tên bệnh")
    @Size(max = 255, message = "Tên tối đa 255 ký tự")
    @Column(nullable = false)
    private String name;

    @Size(max = 2000, message = "Triệu chứng tối đa 2000 ký tự")
    @Column(length = 2000)
    private String symptoms;

    @Size(max = 2000, message = "Cách điều trị tối đa 2000 ký tự")
    @Column(length = 2000)
    private String treatment;

    @ManyToOne(fetch = FetchType.LAZY)
    private Plant plant;
}
