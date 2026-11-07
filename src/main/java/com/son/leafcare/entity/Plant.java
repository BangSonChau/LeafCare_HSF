package com.son.leafcare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Vui lòng nhập tên cây")
    @Size(max = 255, message = "Tên tối đa 255 ký tự")
    @Column(unique = true, nullable = false)
    private String name;

    @Size(max = 255, message = "Tên khoa học tối đa 255 ký tự")
    private String scientificName;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    @Column(length = 2000)
    private String description;
}
