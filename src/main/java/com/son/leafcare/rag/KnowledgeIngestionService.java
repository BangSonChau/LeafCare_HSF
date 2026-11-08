package com.son.leafcare.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.son.leafcare.ai.GeminiGateway;
import com.son.leafcare.config.GeminiProperties;
import com.son.leafcare.entity.KnowledgeChunk;
import com.son.leafcare.entity.KnowledgeSource;
import com.son.leafcare.entity.ReviewStatus;
import com.son.leafcare.repository.KnowledgeChunkRepository;
import com.son.leafcare.repository.KnowledgeSourceRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final String MANIFEST_PATH = "knowledge/manifest.json";

    private final KnowledgeSourceRepository sourceRepo;
    private final KnowledgeChunkRepository chunkRepo;
    private final KnowledgeIndex knowledgeIndex;
    private final GeminiGateway geminiGateway;
    private final GeminiProperties geminiProps;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestionService(
            KnowledgeSourceRepository sourceRepo,
            KnowledgeChunkRepository chunkRepo,
            KnowledgeIndex knowledgeIndex,
            GeminiGateway geminiGateway,
            GeminiProperties geminiProps) {
        this.sourceRepo = sourceRepo;
        this.chunkRepo = chunkRepo;
        this.knowledgeIndex = knowledgeIndex;
        this.geminiGateway = geminiGateway;
        this.geminiProps = geminiProps;
        this.objectMapper = new ObjectMapper();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            ingestDefaultManifest();
            if (geminiProps.isAutoEmbedOnStartup() && geminiGateway.isAvailable()) {
                embedPendingChunks();
            }
            knowledgeIndex.reload();
        } catch (Exception e) {
            log.error("Failed to initialize knowledge manifest on startup: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public int ingestDefaultManifest() {
        ClassPathResource resource = new ClassPathResource(MANIFEST_PATH);
        if (!resource.exists()) {
            log.warn("Knowledge manifest not found at classpath:{}", MANIFEST_PATH);
            return 0;
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode sourcesNode = root.path("sources");
            if (!sourcesNode.isArray()) {
                return 0;
            }
            String defaultReviewer = root.path("reviewedByDefault").asText("LeafCare Team");
            int upsertedChunks = 0;

            for (JsonNode srcNode : sourcesNode) {
                String sourceCode = srcNode.path("sourceCode").asText("").trim();
                if (sourceCode.isEmpty()) {
                    continue;
                }
                String title = srcNode.path("title").asText("").trim();
                String url = srcNode.path("url").asText("").trim();
                String publisher = srcNode.path("publisher").asText("").trim();
                String publishedDate = srcNode.path("publishedDate").asText("");
                String licenseInfo = srcNode.path("licenseInfo").asText("");
                int version = srcNode.path("version").asInt(1);
                String statusStr = srcNode.path("reviewStatus").asText("APPROVED");
                ReviewStatus status = parseStatus(statusStr);
                String reviewedBy = srcNode.path("reviewedBy").asText(defaultReviewer);
                String sourceChecksum = sha256(sourceCode + "|" + title + "|" + url + "|" + version);

                KnowledgeSource source = sourceRepo.findBySourceCode(sourceCode)
                        .orElseGet(KnowledgeSource::new);
                source.setSourceCode(sourceCode);
                source.setTitle(title);
                source.setUrl(url);
                source.setPublisher(publisher);
                source.setPublishedDate(publishedDate);
                source.setLicenseInfo(licenseInfo);
                source.setVersion(version);
                source.setReviewStatus(status);
                source.setReviewedBy(reviewedBy);
                source.setChecksum(sourceChecksum);
                source = sourceRepo.saveAndFlush(source);

                JsonNode chunksNode = srcNode.path("chunks");
                if (chunksNode.isArray()) {
                    for (JsonNode chkNode : chunksNode) {
                        String chunkCode = chkNode.path("chunkCode").asText("").trim();
                        String content = chkNode.path("content").asText("").trim();
                        if (chunkCode.isEmpty() || content.isEmpty()) {
                            continue;
                        }
                        String contentHash = sha256(content);
                        KnowledgeChunk chunk = chunkRepo.findByChunkCode(chunkCode)
                                .orElseGet(KnowledgeChunk::new);
                        boolean contentChanged = !contentHash.equals(chunk.getContentHash());

                        chunk.setChunkCode(chunkCode);
                        chunk.setSource(source);
                        chunk.setSourceVersion(version);
                        chunk.setSectionTitle(chkNode.path("sectionTitle").asText(""));
                        chunk.setContent(content);
                        chunk.setPlantTag(chkNode.path("plantTag").asText(""));
                        chunk.setScientificNameTag(chkNode.path("scientificNameTag").asText(""));
                        chunk.setDiseaseTag(chkNode.path("diseaseTag").asText(""));
                        chunk.setPlantPart(chkNode.path("plantPart").asText(""));
                        chunk.setRegion(chkNode.path("region").asText(""));
                        chunk.setContentHash(contentHash);
                        chunk.setReviewStatus(status);
                        if (chunk.getIndexVersion() == null) {
                            chunk.setIndexVersion(1);
                        }
                        if (contentChanged) {
                            chunk.setVectorJson(null);
                            chunk.setDimension(null);
                            chunk.setEmbeddingModel(null);
                        }
                        chunkRepo.save(chunk);
                        upsertedChunks++;
                    }
                }
            }
            chunkRepo.flush();
            return upsertedChunks;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot ingest knowledge manifest", e);
        }
    }

    public int embedPendingChunks() {
        if (!geminiGateway.isAvailable()) {
            return 0;
        }
        List<KnowledgeChunk> approved = chunkRepo.findByReviewStatusOrderByIdAsc(ReviewStatus.APPROVED);
        int embeddedCount = 0;
        for (KnowledgeChunk chunk : approved) {
            boolean needsEmbed = chunk.getVectorJson() == null
                    || chunk.getVectorJson().isBlank()
                    || !geminiProps.getEmbeddingModel().equals(chunk.getEmbeddingModel())
                    || chunk.getDimension() == null
                    || chunk.getDimension() != geminiProps.getEmbeddingDimension();
            if (!needsEmbed) {
                continue;
            }
            try {
                String textToEmbed = (chunk.getSectionTitle() == null ? "" : chunk.getSectionTitle() + ". ")
                        + chunk.getContent();
                float[] vec = geminiGateway.embed(textToEmbed, false);
                if (vec != null && vec.length > 0) {
                    List<Float> floatList = new ArrayList<>(vec.length);
                    for (float f : vec) {
                        floatList.add(f);
                    }
                    chunk.setVectorJson(objectMapper.writeValueAsString(floatList));
                    chunk.setDimension(vec.length);
                    chunk.setEmbeddingModel(geminiProps.getEmbeddingModel());
                    chunkRepo.save(chunk);
                    embeddedCount++;
                }
            } catch (Exception e) {
                log.warn("Could not embed chunk {}: {}", chunk.getChunkCode(), e.getMessage());
                break; // Stop embedding remaining chunks on quota/network error so startup continues smoothly
            }
        }
        return embeddedCount;
    }

    private ReviewStatus parseStatus(String raw) {
        try {
            return ReviewStatus.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return ReviewStatus.APPROVED;
        }
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

