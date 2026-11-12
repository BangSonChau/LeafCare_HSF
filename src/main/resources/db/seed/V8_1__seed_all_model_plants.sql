-- All 14 plants the scan model knows, each linked by model_prefix, plus the 11 diseases not seeded by V7_1.
-- Loaded by the app (local and Render), not by tests. Idempotent: safe on a DB that already has data.
INSERT INTO plant (name, scientific_name, description)
SELECT v.name, v.sci, v.descr
FROM (VALUES
    ('Táo', 'Malus domestica', 'Cây ăn quả ôn đới, trồng ở vùng cao.'),
    ('Việt quất', 'Vaccinium corymbosum', 'Cây bụi ăn quả, trồng ở Đà Lạt và vùng khí hậu mát.'),
    ('Anh đào', 'Prunus avium', 'Cây ăn quả ôn đới, quả nhỏ màu đỏ.'),
    ('Nho', 'Vitis vinifera', 'Cây dây leo ăn quả, trồng nhiều ở Ninh Thuận.'),
    ('Cam', 'Citrus sinensis', 'Cây ăn quả có múi phổ biến.'),
    ('Đào', 'Prunus persica', 'Cây ăn quả và cây cảnh, trồng ở miền Bắc và vùng cao.'),
    ('Mâm xôi', 'Rubus idaeus', 'Cây bụi ăn quả, quả mọng nhỏ.'),
    ('Đậu nành', 'Glycine max', 'Cây họ đậu lấy hạt.'),
    ('Bí', 'Cucurbita pepo', 'Cây dây leo họ bầu bí, lấy quả.'),
    ('Dâu tây', 'Fragaria × ananassa', 'Cây ăn quả thân thảo, trồng nhiều ở Đà Lạt.')
) AS v(name, sci, descr)
WHERE NOT EXISTS (SELECT 1 FROM plant p WHERE LOWER(p.name) = LOWER(v.name));

UPDATE plant SET model_prefix = 'Apple' WHERE LOWER(name) = LOWER('Táo') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Apple');
UPDATE plant SET model_prefix = 'Blueberry' WHERE LOWER(name) = LOWER('Việt quất') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Blueberry');
UPDATE plant SET model_prefix = 'Cherry' WHERE LOWER(name) = LOWER('Anh đào') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Cherry');
UPDATE plant SET model_prefix = 'Corn' WHERE LOWER(name) = LOWER('Ngô') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Corn');
UPDATE plant SET model_prefix = 'Grape' WHERE LOWER(name) = LOWER('Nho') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Grape');
UPDATE plant SET model_prefix = 'Orange' WHERE LOWER(name) = LOWER('Cam') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Orange');
UPDATE plant SET model_prefix = 'Peach' WHERE LOWER(name) = LOWER('Đào') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Peach');
UPDATE plant SET model_prefix = 'Pepper,_bell' WHERE LOWER(name) = LOWER('Ớt chuông') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Pepper,_bell');
UPDATE plant SET model_prefix = 'Potato' WHERE LOWER(name) = LOWER('Khoai tây') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Potato');
UPDATE plant SET model_prefix = 'Raspberry' WHERE LOWER(name) = LOWER('Mâm xôi') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Raspberry');
UPDATE plant SET model_prefix = 'Soybean' WHERE LOWER(name) = LOWER('Đậu nành') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Soybean');
UPDATE plant SET model_prefix = 'Squash' WHERE LOWER(name) = LOWER('Bí') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Squash');
UPDATE plant SET model_prefix = 'Strawberry' WHERE LOWER(name) = LOWER('Dâu tây') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Strawberry');
UPDATE plant SET model_prefix = 'Tomato' WHERE LOWER(name) = LOWER('Cà chua') AND model_prefix IS NULL AND NOT EXISTS (SELECT 1 FROM plant x WHERE x.model_prefix = 'Tomato');

INSERT INTO disease (name, symptoms, treatment, plant_id, model_label)
SELECT v.name, v.symptoms, v.treatment, p.id, v.label
FROM (VALUES
    ('Bệnh ghẻ táo', 'Đốm màu xanh ô liu đến nâu đen, mặt như nhung trên lá và quả; lá vàng, rụng sớm.', 'Thu dọn lá rụng, tỉa cành cho thông thoáng; phun thuốc trừ nấm từ lúc nảy chồi theo khuyến cáo.', 'Táo', 'Apple___Apple_scab'),
    ('Bệnh thối đen', 'Đốm tròn nhỏ màu tím trên lá, lớn dần thành đốm nâu viền tím (mắt ếch); quả thối đen và teo lại.', 'Cắt bỏ cành khô và quả teo, vệ sinh vườn; phun thuốc trừ nấm theo khuyến cáo.', 'Táo', 'Apple___Black_rot'),
    ('Bệnh gỉ sắt táo', 'Đốm vàng cam sáng trên mặt lá, mặt dưới có các ống nhỏ chứa bào tử; nấm sống luân phiên trên cây bách xù.', 'Không trồng gần cây bách xù, dùng giống kháng; phun thuốc trừ nấm đầu mùa mưa.', 'Táo', 'Apple___Cedar_apple_rust'),
    ('Bệnh phấn trắng', 'Lớp bột trắng trên lá non; lá quăn, biến dạng và kém phát triển.', 'Tỉa cành cho thông thoáng, tránh bón thừa đạm; phun lưu huỳnh hoặc thuốc trừ phấn trắng khi chớm bệnh.', 'Anh đào', 'Cherry___Powdery_mildew'),
    ('Bệnh thối đen nho', 'Đốm nâu đỏ viền đen trên lá, giữa có chấm đen nhỏ; quả khô đen, teo như nho khô.', 'Dọn quả teo và lá bệnh, tỉa giàn thông thoáng; phun thuốc trừ nấm từ khi ra hoa.', 'Nho', 'Grape___Black_rot'),
    ('Bệnh esca (sởi đen)', 'Lá có sọc vàng hoặc nâu đỏ giữa các gân như vằn hổ; quả có chấm đen; cành có thể chết đột ngột.', 'Cắt bỏ phần gỗ bệnh, bôi thuốc bảo vệ vết cắt; chưa có thuốc chữa dứt điểm.', 'Nho', 'Grape___Esca_(Black_Measles)'),
    ('Bệnh cháy lá nho', 'Đốm nâu sẫm không đều trên lá, lan rộng làm lá khô và rụng sớm.', 'Dọn lá bệnh, giữ giàn thông thoáng; phun thuốc trừ nấm theo khuyến cáo.', 'Nho', 'Grape___Leaf_blight_(Isariopsis_Leaf_Spot)'),
    ('Bệnh vàng lá gân xanh', 'Lá vàng loang lổ không đối xứng, gân lá còn xanh; quả nhỏ, méo, chua. Do vi khuẩn, rầy chổng cánh truyền.', 'Không có thuốc chữa: nhổ bỏ cây bệnh, trừ rầy chổng cánh, trồng cây giống sạch bệnh.', 'Cam', 'Orange___Haunglongbing_(Citrus_greening)'),
    ('Bệnh đốm vi khuẩn đào', 'Đốm nhỏ góc cạnh màu tím nâu, phần giữa rơi ra để lại lỗ thủng; lá vàng và rụng.', 'Dùng giống kháng, tỉa cành thông thoáng; phun thuốc gốc đồng giai đoạn ngủ nghỉ và nảy chồi.', 'Đào', 'Peach___Bacterial_spot'),
    ('Bệnh phấn trắng bí', 'Đốm bột trắng trên mặt lá, lan phủ kín lá; lá vàng rồi khô.', 'Trồng thưa, tưới vào gốc; phun lưu huỳnh hoặc thuốc trừ phấn trắng khi chớm bệnh.', 'Bí', 'Squash___Powdery_mildew'),
    ('Bệnh cháy lá dâu tây', 'Nhiều đốm nhỏ màu tím sẫm, lan rộng làm lá trông như bị cháy, mép lá khô.', 'Cắt bỏ lá bệnh, tránh tưới lên lá, trồng thưa; phun thuốc trừ nấm theo khuyến cáo.', 'Dâu tây', 'Strawberry___Leaf_scorch')
) AS v(name, symptoms, treatment, plant_name, label)
JOIN plant p ON LOWER(p.name) = LOWER(v.plant_name)
WHERE NOT EXISTS (SELECT 1 FROM disease d WHERE d.model_label = v.label);
