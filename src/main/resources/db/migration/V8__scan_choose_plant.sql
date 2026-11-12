-- Plants the scan model knows carry the model's label prefix (e.g. 'Tomato' for 'Tomato___Late_blight').
ALTER TABLE plant ADD COLUMN model_prefix VARCHAR(50);
ALTER TABLE plant ADD CONSTRAINT uk_plant_model_prefix UNIQUE (model_prefix);
-- The plant the user picked before scanning (null = let the model decide).
ALTER TABLE scan_history ADD COLUMN chosen_plant_id BIGINT;
ALTER TABLE scan_history ADD CONSTRAINT fk_scan_chosen_plant FOREIGN KEY (chosen_plant_id) REFERENCES plant (id);
-- Set when the photo looks clearly like another plant than the one picked.
ALTER TABLE scan_history ADD COLUMN looks_like_plant_id BIGINT;
ALTER TABLE scan_history ADD CONSTRAINT fk_scan_looks_like_plant FOREIGN KEY (looks_like_plant_id) REFERENCES plant (id);
-- The model's 3 best guesses, "label=confidence" joined by ';'.
ALTER TABLE scan_history ADD COLUMN top3 VARCHAR(600);
