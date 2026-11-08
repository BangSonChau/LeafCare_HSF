-- V2.1: Additional home garden plants and diseases for Phase 3 RAG
INSERT INTO plant (name, scientific_name, description)
SELECT v.name, v.sci, v.descr
FROM (VALUES
    ('Ớt', 'Capsicum annuum', 'Cây gia vị trồng chậu hoặc vườn nhà, thường gặp bệnh thán thư, xoăn lá và rệp chích hút.'),
    ('Hoa hồng', 'Rosa chinensis', 'Cây hoa cảnh phổ biến ở ban công và sân vườn, nhạy cảm với bệnh đốm đen, phấn trắng và nhện đỏ.'),
    ('Lan hồ điệp', 'Phalaenopsis spp.', 'Cây hoa cảnh trồng trong nhà và hiên mát, cần giá thể thoáng, dễ bị thối nhũn và cháy nắng.'),
    ('Trầu bà', 'Epipremnum aureum', 'Cây cảnh nội thất dễ trồng, hay gặp vàng lá thối rễ do tưới dư nước hoặc cháy chóp lá.'),
    ('Dưa leo', 'Cucumis sativus', 'Cây rau leo giàn tại nhà, dễ nhiễm sương mai và phấn trắng vào mùa ẩm.')
) AS v(name, sci, descr)
WHERE NOT EXISTS (SELECT 1 FROM plant p WHERE LOWER(p.name) = LOWER(v.name));

INSERT INTO disease (name, symptoms, treatment, plant_id)
SELECT v.name, v.symptoms, v.treatment, p.id
FROM (VALUES
    ('Bệnh thán thư ớt', 'Vết lõm tròn đồng tâm trên quả và lá già, giữa vết có các chấm đen nhỏ hoặc màu hồng cam.', 'Thu gom quả bệnh, thoát nước tốt, bón cân đối canxi-kali và luân canh.', 'Ớt'),
    ('Bệnh đốm đen hoa hồng', 'Đốm tròn màu đen tím có viền tua rua trên mặt trên lá, vùng quanh đốm hóa vàng rồi rụng sớm.', 'Tỉa cành thông thoáng, nhặt sạch lá rụng dưới gốc, tưới vào gốc buổi sáng.', 'Hoa hồng'),
    ('Bệnh thối nhũn lan', 'Vết mọng nước màu nâu nhạt trên lá non hoặc ngọn, lan nhanh và có mùi hôi khó chịu.', 'Cách ly cây ngay, cắt bỏ phần thối bằng dao khử trùng, bôi vôi hoặc keo liền sẹo và ngừng tưới 3-5 ngày.', 'Lan hồ điệp'),
    ('Thối rễ vàng lá do úng nước', 'Lá già vàng rũ từ dưới lên dù đất vẫn ướt, gốc thân mềm nâu, rễ chuyển đen và tuột vỏ.', 'Ngừng tưới, kiểm tra lỗ thoát nước, cắt bỏ rễ thối và thay giá thể tơi xốp thoáng khí.', 'Trầu bà'),
    ('Bệnh sương mai dưa leo', 'Đốm vàng góc cạnh giới hạn bởi gân lá ở mặt trên, mặt dưới có lớp mốc xám tím.', 'Làm giàn thông thoáng, tỉa lá già chân gốc, tránh tưới phun mưa vào chiều tối.', 'Dưa leo')
) AS v(name, symptoms, treatment, plant_name)
JOIN plant p ON LOWER(p.name) = LOWER(v.plant_name)
WHERE NOT EXISTS (SELECT 1 FROM disease d WHERE d.name = v.name);

