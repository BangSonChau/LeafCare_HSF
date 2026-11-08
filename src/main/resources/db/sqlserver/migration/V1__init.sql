CREATE TABLE plant (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    scientific_name VARCHAR(255),
    description VARCHAR(2000),
    CONSTRAINT uk_plant_name UNIQUE (name)
);

CREATE TABLE disease (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    symptoms VARCHAR(2000),
    treatment VARCHAR(2000),
    plant_id BIGINT,
    CONSTRAINT fk_disease_plant FOREIGN KEY (plant_id) REFERENCES plant (id)
);

CREATE TABLE scan_history (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    image_file_name VARCHAR(255) NOT NULL,
    note VARCHAR(1000),
    status VARCHAR(255) NOT NULL,
    confidence FLOAT(53),
    plant_id BIGINT,
    disease_id BIGINT,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT fk_scan_plant FOREIGN KEY (plant_id) REFERENCES plant (id),
    CONSTRAINT fk_scan_disease FOREIGN KEY (disease_id) REFERENCES disease (id)
);
