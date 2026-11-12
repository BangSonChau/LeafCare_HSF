-- Scan analysis by the leaf-disease model: each disease may name the model label it matches,
-- and each scan keeps the raw label the model returned (healthy / no leaf / not in the catalog have no disease row).
ALTER TABLE disease ADD COLUMN model_label VARCHAR(100);
ALTER TABLE disease ADD CONSTRAINT uk_disease_model_label UNIQUE (model_label);
ALTER TABLE scan_history ADD COLUMN model_label VARCHAR(100);
