package com.son.leafcare.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.son.leafcare.ai.GeminiGateway;
import com.son.leafcare.rag.CitationValidator.ValidatedCitation;
import com.son.leafcare.rag.KnowledgeIndex.IndexedChunk;
import com.son.leafcare.rag.RetrievalService.RetrievalResult;
import com.son.leafcare.repository.KnowledgeChunkRepository;
import com.son.leafcare.repository.KnowledgeSourceRepository;
import com.son.leafcare.config.GeminiProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RetrievalAndCitationTest {

    @Autowired
    KnowledgeSourceRepository sourceRepo;

    @Autowired
    KnowledgeChunkRepository chunkRepo;

    InMemoryKnowledgeIndex knowledgeIndex;
    RetrievalService retrievalService;
    CitationValidator citationValidator;
    GeminiGateway mockGateway;

    @BeforeEach
    void setUp() {
        mockGateway = mock(GeminiGateway.class);
        when(mockGateway.isAvailable()).thenReturn(false);

        GeminiProperties props = new GeminiProperties();
        props.setEnabled(false);

        knowledgeIndex = new InMemoryKnowledgeIndex(chunkRepo);
        KnowledgeIngestionService ingestion = new KnowledgeIngestionService(
                sourceRepo, chunkRepo, knowledgeIndex, mockGateway, props);
        int count = ingestion.ingestDefaultManifest();
        assertThat(count).isGreaterThanOrEqualTo(10);
        knowledgeIndex.reload();

        retrievalService = new RetrievalService(knowledgeIndex, mockGateway);
        citationValidator = new CitationValidator();
    }

    @Test
    void normalizerHandlesVietnameseDiacriticsAndSynonyms() {
        assertThat(VietnameseTextNormalizer.stripAccents("Bệnh đốm đen trên Hoa Hồng"))
                .isEqualTo("benh dom den tren hoa hong");
        assertThat(VietnameseTextNormalizer.detectPlantTag("Lá cây trầu bà bị vàng rũ"))
                .isEqualTo("Trầu bà");
        assertThat(VietnameseTextNormalizer.detectPlantTag("Quả cà chua bị thối đít"))
                .isEqualTo("Cà chua");
    }

    @Test
    void retrievesCorrectChunksAcrossVietnameseEvaluationSuite() {
        record EvalCase(String query, String preferredPlant, String expectedChunkPrefix) {}

        List<EvalCase> suite = List.of(
                new EvalCase("Lá cà chua có đốm nâu sẫm mép lá, mặt dưới có mốc trắng xám khi trời ẩm", "Cà chua", "TOMATO-LATE-BLIGHT"),
                new EvalCase("ca chua bi moc suong phai xu ly canh tac va phong ngua ra sao", null, "TOMATO-LATE-BLIGHT"),
                new EvalCase("lá già cà chua gần gốc có đốm tròn vòng đồng tâm màu đen và viền vàng", "Cà chua", "TOMATO-EARLY-BLIGHT"),
                new EvalCase("đáy quả cà chua bị lõm khô màu nâu đen như da thuộc do thiếu canxi", "Cà chua", "TOMATO-BER-CARE"),
                new EvalCase("quả ớt bị vết lõm đồng tâm có chấm đen và bào tử hồng cam", "Ớt", "CHILI-ANTHRACNOSE"),
                new EvalCase("đọt non cây ớt bị xoăn tít cong vênh do bọ trĩ và rệp", "Ớt", "CHILI-LEAF-CURL"),
                new EvalCase("lá hoa hồng có đốm tròn màu đen viền tua rua rồi vàng rụng", "Hoa hồng", "ROSE-BLACK-SPOT"),
                new EvalCase("hoa hong ban cong bi lop bot trang tren nu va nhen do mat duoi la", "Hoa hồng", "ROSE-POWDERY-MITE"),
                new EvalCase("lá lan hồ điệp có vết mọng nước nâu nhạt lan nhanh mùi hôi tanh", "Lan hồ điệp", "ORCHID-SOFT-ROT"),
                new EvalCase("lan hồ điệp bị cháy nắng mảng trắng khô lõm và vàng lá chân sinh lý", "Lan hồ điệp", "ORCHID-SUNBURN-CARE"),
                new EvalCase("cây trầu bà vàng lá hàng loạt dù đất ướt sũng, rễ đen tuột vỏ", "Trầu bà", "POTHOS-ROOT-ROT"),
                new EvalCase("trầu bà trồng phòng máy lạnh bị khô cháy chóp lá màu nâu và mất vân trắng", "Trầu bà", "POTHOS-LEAF-TIP"),
                new EvalCase("lá dưa leo có đốm vàng đa giác góc cạnh giới hạn bởi gân lá, mặt dưới mốc tím", "Dưa leo", "CUCUMBER-DOWNY-MILDEW"),
                new EvalCase("lúa bị vết bệnh hình thoi mắt én tâm xám viền nâu", "Lúa", "RICE-BLAST"),
                new EvalCase("mặt dưới lá cà phê có lớp bột màu vàng cam như gỉ sắt", "Cà phê", "COFFEE-RUST")
        );

        int hitsInTop5 = 0;
        for (EvalCase tc : suite) {
            RetrievalResult res = retrievalService.retrieve(tc.query(), tc.preferredPlant(), "");
            boolean hit = res.indexedChunks().stream()
                    .limit(5)
                    .anyMatch(c -> c.chunkCode().startsWith(tc.expectedChunkPrefix()));
            if (hit) {
                hitsInTop5++;
            }
        }
        double hitRate = (double) hitsInTop5 / suite.size();
        assertThat(hitRate).isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void citationValidatorRejectsHallucinatedIdsAndKeepsRetrievedOnes() {
        RetrievalResult res = retrievalService.retrieve("Hoa hồng bị đốm đen viền tua rua", "Hoa hồng", "");
        List<IndexedChunk> retrieved = res.indexedChunks();
        assertThat(retrieved).isNotEmpty();

        List<String> modelOutputIds = List.of(
                "ROSE-BLACK-SPOT-01",
                "FAKE-HALLUCINATED-ID-999",
                "[ROSE-BLACK-SPOT-01]" // duplicate with brackets
        );

        List<ValidatedCitation> validated = citationValidator.validateAndMap(modelOutputIds, retrieved);
        assertThat(validated).hasSize(1);
        assertThat(validated.get(0).chunk().chunkCode()).isEqualTo("ROSE-BLACK-SPOT-01");
        assertThat(validated.get(0).chunk().sourceUrl()).startsWith("https://");
    }
}

