-- Sample data for local dev (not loaded in tests). Safe to run on a DB that already has data.
INSERT INTO plant (name, scientific_name, description)
SELECT v.name, v.sci, v.descr
FROM (VALUES
    ('Cà chua', 'Solanum lycopersicum', 'Cây rau ăn quả phổ biến, dễ bị bệnh lá khi ẩm.'),
    ('Lúa', 'Oryza sativa', 'Cây lương thực chính ở Việt Nam.'),
    ('Cà phê', 'Coffea canephora', 'Cây công nghiệp lâu năm ở Tây Nguyên.')
) AS v(name, sci, descr)
WHERE NOT EXISTS (SELECT 1 FROM plant p WHERE LOWER(p.name) = LOWER(v.name));

INSERT INTO disease (name, symptoms, treatment, plant_id)
SELECT v.name, v.symptoms, v.treatment, p.id
FROM (VALUES
    ('Bệnh mốc sương', 'Đốm nâu sẫm, mặt dưới lá có mốc trắng.', 'Cắt bỏ lá bệnh, phun thuốc gốc đồng.', 'Cà chua'),
    ('Bệnh đạo ôn', 'Vết bệnh hình thoi, tâm xám, viền nâu.', 'Giảm đạm, phun thuốc đặc trị đạo ôn.', 'Lúa'),
    ('Bệnh gỉ sắt', 'Mặt dưới lá có bột màu vàng cam.', 'Tỉa cành cho thoáng, phun thuốc gốc đồng.', 'Cà phê')
) AS v(name, symptoms, treatment, plant_name)
JOIN plant p ON LOWER(p.name) = LOWER(v.plant_name)
WHERE NOT EXISTS (SELECT 1 FROM disease d WHERE d.name = v.name);
