-- Diseases the scan model can recognise for the 4 supported crops, linked by model_label.
-- Loaded by the app (local and Render), not by tests. Idempotent: safe on a DB that already has data.
INSERT INTO plant (name, scientific_name, description)
SELECT v.name, v.sci, v.descr
FROM (VALUES
    ('Cà chua', 'Solanum lycopersicum', 'Cây rau ăn quả phổ biến, dễ bị bệnh lá khi ẩm.'),
    ('Khoai tây', 'Solanum tuberosum', 'Cây lấy củ, trồng nhiều ở Đà Lạt và đồng bằng Bắc Bộ vụ đông.'),
    ('Ớt chuông', 'Capsicum annuum', 'Cây rau ăn quả họ cà, trồng nhiều trong nhà màng.'),
    ('Ngô', 'Zea mays', 'Cây lương thực và thức ăn chăn nuôi.')
) AS v(name, sci, descr)
WHERE NOT EXISTS (SELECT 1 FROM plant p WHERE LOWER(p.name) = LOWER(v.name));

-- The demo seed already has "Bệnh mốc sương" on tomato: link it instead of adding a duplicate.
UPDATE disease SET model_label = 'Tomato___Late_blight'
WHERE name = 'Bệnh mốc sương' AND model_label IS NULL
  AND plant_id = (SELECT id FROM plant WHERE LOWER(name) = LOWER('Cà chua'))
  AND NOT EXISTS (SELECT 1 FROM disease d WHERE d.model_label = 'Tomato___Late_blight');

INSERT INTO disease (name, symptoms, treatment, plant_id, model_label)
SELECT v.name, v.symptoms, v.treatment, p.id, v.label
FROM (VALUES
    ('Bệnh đốm vi khuẩn', 'Đốm nhỏ màu nâu đen, ướt, có quầng vàng; đốm liền nhau làm lá cháy và rụng.', 'Dùng hạt giống sạch bệnh, tránh tưới lên lá, cắt bỏ lá bệnh; phun thuốc gốc đồng khi bệnh chớm xuất hiện.', 'Cà chua', 'Tomato___Bacterial_spot'),
    ('Bệnh đốm vòng (đốm sớm)', 'Đốm nâu có các vòng tròn đồng tâm như hình bia, thường bắt đầu ở lá già phía dưới, lá vàng rồi khô.', 'Ngắt bỏ lá già bị bệnh, luân canh, giữ ruộng thông thoáng; phun thuốc trừ nấm theo khuyến cáo.', 'Cà chua', 'Tomato___Early_blight'),
    ('Bệnh mốc sương', 'Vết bệnh màu nâu sẫm, lan nhanh khi trời lạnh ẩm; mặt dưới lá có lớp mốc trắng.', 'Cắt bỏ và tiêu huỷ lá bệnh, không tưới chiều tối, phun thuốc gốc đồng hoặc thuốc trừ mốc sương khi thời tiết ẩm.', 'Cà chua', 'Tomato___Late_blight'),
    ('Bệnh mốc lá', 'Mặt trên lá có đốm vàng nhạt, mặt dưới có lớp mốc màu xám nâu như nhung; hay gặp trong nhà màng ẩm.', 'Giảm độ ẩm, tăng thông gió, tỉa lá gốc; phun thuốc trừ nấm theo khuyến cáo.', 'Cà chua', 'Tomato___Leaf_Mold'),
    ('Bệnh đốm lá Septoria', 'Nhiều đốm tròn nhỏ, tâm màu xám trắng, viền nâu sẫm, có chấm đen li ti ở giữa; bắt đầu từ lá dưới.', 'Ngắt bỏ lá bệnh, tránh làm ướt lá, phủ gốc chống bắn đất; phun thuốc trừ nấm theo khuyến cáo.', 'Cà chua', 'Tomato___Septoria_leaf_spot'),
    ('Nhện đỏ hai chấm', 'Lá có nhiều chấm vàng li ti, sau đó bạc màu, khô; mặt dưới lá có nhện rất nhỏ và tơ mảnh.', 'Tưới phun rửa mặt dưới lá, cắt bỏ lá bị nặng; dùng thuốc trừ nhện khi mật độ cao.', 'Cà chua', 'Tomato___Spider_mites Two-spotted_spider_mite'),
    ('Bệnh đốm nâu (đốm mục tiêu)', 'Đốm nâu tròn có vòng đồng tâm và quầng vàng, có thể xuất hiện trên lá, thân và quả.', 'Tỉa lá cho thông thoáng, dọn tàn dư cây bệnh; phun thuốc trừ nấm theo khuyến cáo.', 'Cà chua', 'Tomato___Target_Spot'),
    ('Bệnh xoăn vàng lá', 'Lá non nhỏ, xoăn mép lên trên, vàng ở mép lá; cây lùn, ít hoa quả. Do virus, bọ phấn trắng truyền.', 'Không có thuốc chữa virus: nhổ bỏ cây bệnh, trừ bọ phấn trắng, dùng giống kháng và lưới chắn côn trùng.', 'Cà chua', 'Tomato___Tomato_Yellow_Leaf_Curl_Virus'),
    ('Bệnh khảm lá', 'Lá loang lổ xanh đậm xanh nhạt như khảm, có thể nhăn và biến dạng; cây còi cọc. Do virus.', 'Không có thuốc chữa: nhổ bỏ cây bệnh, rửa tay và dụng cụ sau khi chạm cây bệnh, dùng giống sạch bệnh.', 'Cà chua', 'Tomato___Tomato_mosaic_virus'),
    ('Bệnh đốm vòng (đốm sớm)', 'Đốm nâu có vòng đồng tâm, thường ở lá già; nặng thì lá vàng và khô.', 'Bón phân cân đối, luân canh, ngắt lá bệnh; phun thuốc trừ nấm theo khuyến cáo.', 'Khoai tây', 'Potato___Early_blight'),
    ('Bệnh mốc sương', 'Vết bệnh xanh xám rồi nâu đen, lan rất nhanh khi lạnh ẩm; mặt dưới lá có mốc trắng.', 'Dùng củ giống sạch bệnh, vun gốc cao, tiêu huỷ cây bệnh; phun thuốc trừ mốc sương khi thời tiết ẩm.', 'Khoai tây', 'Potato___Late_blight'),
    ('Bệnh đốm vi khuẩn', 'Đốm nhỏ màu nâu, hơi ướt, có quầng vàng; lá vàng và rụng sớm.', 'Dùng hạt giống sạch bệnh, tránh tưới lên lá, luân canh; phun thuốc gốc đồng khi bệnh chớm xuất hiện.', 'Ớt chuông', 'Pepper,_bell___Bacterial_spot'),
    ('Bệnh đốm lá xám', 'Vết bệnh hình chữ nhật hẹp màu xám nâu, chạy dọc theo gân lá.', 'Luân canh, cày vùi tàn dư, dùng giống chống chịu; phun thuốc trừ nấm khi bệnh nặng.', 'Ngô', 'Corn___Cercospora_leaf_spot Gray_leaf_spot'),
    ('Bệnh gỉ sắt', 'Nhiều ổ nấm nhỏ nổi lên màu nâu đỏ như gỉ sắt ở cả hai mặt lá.', 'Dùng giống kháng, gieo đúng thời vụ; phun thuốc trừ nấm khi bệnh xuất hiện sớm.', 'Ngô', 'Corn___Common_rust'),
    ('Bệnh cháy lá lớn', 'Vết bệnh dài hình thoi màu xám xanh rồi nâu, dài vài cm, làm lá khô sớm.', 'Luân canh, vệ sinh tàn dư, dùng giống kháng; phun thuốc trừ nấm theo khuyến cáo.', 'Ngô', 'Corn___Northern_Leaf_Blight')
) AS v(name, symptoms, treatment, plant_name, label)
JOIN plant p ON LOWER(p.name) = LOWER(v.plant_name)
WHERE NOT EXISTS (SELECT 1 FROM disease d WHERE d.model_label = v.label);
