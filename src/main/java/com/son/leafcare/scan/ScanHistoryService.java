package com.son.leafcare.scan;

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.disease.Disease;
import com.son.leafcare.disease.DiseaseRepository;
import com.son.leafcare.plan.SubscriptionService;
import com.son.leafcare.plant.Plant;
import com.son.leafcare.plant.PlantRepository;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.Role;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Scans belong to their owner; ADMIN sees all. Free accounts get FREE_PER_DAY scans per Vietnam day. */
@Service
public class ScanHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ScanHistoryService.class);

    static final int PAGE_SIZE = 10;
    public static final int FREE_PER_DAY = 5;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ScanHistoryRepository repo;
    private final FileStorageService files;
    private final SubscriptionService subscriptions;
    private final ScanClient model;
    private final DiseaseRepository diseases;
    private final PlantRepository plants;

    /** Model label for a photo without a leaf. */
    static final String NO_LEAF = "Background_without_leaves";
    /** Above this, a photo of another plant than the chosen one is reported to the user. */
    static final double LOOKS_LIKE = 0.5;

    /** One of the model's guesses, ready to show: disease (or "Lá khoẻ" / "Không thấy lá"), plant, confidence. */
    public record Guess(String name, String plant, double confidence) {
    }

    public ScanHistoryService(ScanHistoryRepository repo, FileStorageService files, SubscriptionService subscriptions,
                              ScanClient model, DiseaseRepository diseases, PlantRepository plants) {
        this.repo = repo;
        this.files = files;
        this.subscriptions = subscriptions;
        this.model = model;
        this.diseases = diseases;
        this.plants = plants;
    }

    /** Plants the user can pick on the scan form (the ones the model knows). */
    @Transactional(readOnly = true)
    public List<Plant> scannablePlants() {
        return plants.findByModelPrefixIsNotNullOrderByName();
    }

    /** Scans left today, or null when the user has no limit (Premium or ADMIN). */
    @Transactional(readOnly = true)
    public Integer remainingToday(AppUser user) {
        if (user.getRole() == Role.ADMIN || subscriptions.isPremium(user)) {
            return null;
        }
        long used = repo.countByOwnerAndCreatedAtGreaterThanEqual(user, startOfVietnamDay());
        return (int) Math.max(FREE_PER_DAY - used, 0);
    }

    /** Checks the Free limit before storing anything, so a blocked scan leaves no file behind. */
    @Transactional
    public ScanHistory create(MultipartFile f, String note, Long plantId, AppUser user) {
        // shortcut: two uploads at the same instant can both pass this check (6th scan), fine for a demo app
        Integer left = remainingToday(user);
        if (left != null && left == 0) {
            throw new ScanLimitException();
        }
        Plant chosen = plantId == null ? null
                : plants.findById(plantId).filter(pl -> pl.getModelPrefix() != null).orElseThrow(NotFoundException::new);
        String name = files.store(f);
        ScanHistory s = new ScanHistory();
        s.setImageFileName(name);
        s.setOwner(user);
        s.setChosenPlant(chosen);
        String trimmed = note == null ? "" : note.trim();
        s.setNote(trimmed.isEmpty() ? null : trimmed);
        return repo.saveAndFlush(s); // image and row share this transaction, so a failure rolls back both
    }

    @Transactional(readOnly = true)
    public Page<ScanHistory> list(AppUser user, int page) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Page<ScanHistory> result = find(user, PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            return find(user, PageRequest.of(Math.max(result.getTotalPages() - 1, 0), PAGE_SIZE, sort));
        }
        return result;
    }

    /** The scan, if this user may see it; someone else's scan is 404 like a missing one. */
    @Transactional(readOnly = true)
    public ScanHistory get(Long id, AppUser user) {
        return repo.findById(id)
                .filter(s -> user.getRole() == Role.ADMIN
                        || (s.getOwner() != null && Objects.equals(s.getOwner().getId(), user.getId())))
                .orElseThrow(NotFoundException::new);
    }

    /**
     * Sends the scan's photo to the model and stores its answer. With a chosen plant only that plant's labels count
     * (unless the photo has no leaf), and a photo that clearly shows another plant sets looksLikePlant. A known label
     * links the disease; the result plant comes from the label even for healthy leaves. When the model service fails the scan becomes FAILED
     * and the user can retry. No DB transaction is held while waiting for the model (up to 60s).
     */
    public ScanHistory analyze(Long id, AppUser user) {
        ScanHistory s = get(id, user);
        ImageFile image = files.find(s.getImageFileName()).orElse(null);
        try {
            if (image == null) {
                throw new IllegalStateException("Image " + s.getImageFileName() + " is missing");
            }
            long started = System.nanoTime();
            ScanClient.Prediction p = model.predict(image.getData(), image.getName());
            Map<String, Double> probs = p.probs() == null || p.probs().isEmpty()
                    ? Map.of(p.label(), p.confidence()) : p.probs();
            List<Map.Entry<String, Double>> all = ranked(probs, null);
            List<Map.Entry<String, Double>> pick = all;
            s.setLooksLikePlant(null);
            // getId() on the lazy proxy needs no session; the plant itself is loaded here (no transaction is open).
            Plant chosen = s.getChosenPlant() == null ? null : plants.findById(s.getChosenPlant().getId()).orElse(null);
            String seen = all.get(0).getKey();
            if (chosen != null && chosen.getModelPrefix() != null && !seen.equals(NO_LEAF)) {
                List<Map.Entry<String, Double>> mine = ranked(probs, chosen.getModelPrefix());
                if (!mine.isEmpty()) {
                    pick = mine;
                    if (!prefix(seen).equals(chosen.getModelPrefix()) && all.get(0).getValue() >= LOOKS_LIKE) {
                        s.setLooksLikePlant(plants.findByModelPrefix(prefix(seen)).orElse(null));
                    }
                }
            }
            String label = pick.get(0).getKey();
            Disease d = diseases.findByModelLabel(label).orElse(null);
            s.setModelLabel(label);
            s.setConfidence(pick.get(0).getValue());
            s.setTop3(pick.stream().limit(3)
                    .map(e -> e.getKey() + "=" + String.format(Locale.ROOT, "%.4f", e.getValue()))
                    .collect(Collectors.joining(";")));
            s.setDisease(d);
            s.setPlant(d != null ? d.getPlant() : plants.findByModelPrefix(prefix(label)).orElse(null));
            s.setStatus(ScanStatus.DONE);
            log.info("Scan {} analysed in {}s: {} {}% (chosen plant: {})", id,
                    String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e9), label,
                    Math.round(s.getConfidence() * 100), chosen == null ? "auto" : chosen.getModelPrefix());
        } catch (RuntimeException e) {
            log.warn("Scan {} analysis failed: {}", id, e.toString());
            s.setStatus(ScanStatus.FAILED);
        }
        return repo.save(s);
    }

    /** The scan's top 3 guesses with Vietnamese names; empty before analysis. */
    @Transactional(readOnly = true)
    public List<Guess> guesses(ScanHistory s) {
        if (s.getTop3() == null || s.getTop3().isBlank()) {
            return List.of();
        }
        List<Guess> out = new ArrayList<>();
        for (String part : s.getTop3().split(";")) {
            int eq = part.lastIndexOf('=');
            String label = part.substring(0, eq);
            double conf = Double.parseDouble(part.substring(eq + 1));
            String plant = plants.findByModelPrefix(prefix(label)).map(Plant::getName).orElse(null);
            String name = diseases.findByModelLabel(label).map(Disease::getName)
                    .orElse(label.equals(NO_LEAF) ? "Không thấy lá" : label.endsWith("healthy") ? "Lá khoẻ" : label);
            out.add(new Guess(name, plant, conf));
        }
        return out;
    }

    /** Labels by probability; with a prefix only that plant's labels, renormalised so they add up to 1. */
    static List<Map.Entry<String, Double>> ranked(Map<String, Double> probs, String prefix) {
        List<Map.Entry<String, Double>> kept = probs.entrySet().stream()
                .filter(e -> prefix == null || prefix(e.getKey()).equals(prefix))
                .toList();
        double sum = kept.stream().mapToDouble(Map.Entry::getValue).sum();
        return kept.stream()
                .map(e -> Map.entry(e.getKey(), prefix != null && sum > 0 ? e.getValue() / sum : e.getValue()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
    }

    /** "Tomato___Late_blight" -> "Tomato"; a label without "___" is its own prefix. */
    static String prefix(String label) {
        int i = label.indexOf("___");
        return i < 0 ? label : label.substring(0, i);
    }

    @Transactional
    public void delete(Long id, AppUser user) {
        ScanHistory s = get(id, user);
        repo.delete(s);
        repo.flush();
        files.delete(s.getImageFileName());
    }

    private Page<ScanHistory> find(AppUser user, Pageable p) {
        return user.getRole() == Role.ADMIN ? repo.findAll(p) : repo.findByOwner(user, p);
    }

    /** 00:00 today in Vietnam, in the server's local time (createdAt is stored as server local time). */
    public static LocalDateTime startOfVietnamDay() {
        return ZonedDateTime.now(VN).toLocalDate().atStartOfDay(VN)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
