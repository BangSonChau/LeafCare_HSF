-- Sample data for local dev (not loaded in tests). Safe to run on a DB that already has data.
INSERT INTO plant (name, scientific_name, description)
SELECT v.name, v.sci, v.descr
FROM (VALUES
    (N'Cà chua', N'Solanum lycopersicum', N'Cây rau ăn quả phổ biến, dễ bị bệnh lá khi ẩm.'),
    (N'Lúa', N'Oryza sativa', N'Cây lương thực chính ở Việt Nam.'),
    (N'Cà phê', N'Coffea canephora', N'Cây công nghiệp lâu năm ở Tây Nguyên.')
) AS v(name, sci, descr)
WHERE NOT EXISTS (SELECT 1 FROM plant p WHERE LOWER(p.name) = LOWER(v.name));

INSERT INTO disease (name, symptoms, treatment, plant_id)
SELECT v.name, v.symptoms, v.treatment, p.id
FROM (VALUES
    (N'Bệnh mốc sương', N'Đốm nâu sẫm, mặt dưới lá có mốc trắng.', N'Cắt bỏ lá bệnh, phun thuốc gốc đồng.', N'Cà chua'),
    (N'Bệnh đạo ôn', N'Vết bệnh hình thoi, tâm xám, viền nâu.', N'Giảm đạm, phun thuốc đặc trị đạo ôn.', N'Lúa'),
    (N'Bệnh gỉ sắt', N'Mặt dưới lá có bột màu vàng cam.', N'Tỉa cành cho thoáng, phun thuốc gốc đồng.', N'Cà phê')
) AS v(name, symptoms, treatment, plant_name)
JOIN plant p ON LOWER(p.name) = LOWER(v.plant_name)
WHERE NOT EXISTS (SELECT 1 FROM disease d WHERE d.name = v.name);
