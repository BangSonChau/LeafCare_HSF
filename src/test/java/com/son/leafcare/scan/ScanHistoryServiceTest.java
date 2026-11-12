package com.son.leafcare.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.disease.Disease;
import com.son.leafcare.disease.DiseaseRepository;
import com.son.leafcare.plan.SubscriptionService;
import com.son.leafcare.plant.Plant;
import com.son.leafcare.plant.PlantRepository;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.AppUserRepository;
import com.son.leafcare.user.Role;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

@DataJpaTest
@ExtendWith(OutputCaptureExtension.class)
@Import({ScanHistoryService.class, FileStorageService.class, SubscriptionService.class})
class ScanHistoryServiceTest {

    @MockitoBean
    ScanClient model;

    @Autowired
    DiseaseRepository diseases;

    @Autowired
    PlantRepository plants;

    @Autowired
    ScanHistoryService service;

    @Autowired
    ScanHistoryRepository repo;

    @Autowired
    AppUserRepository users;

    @Autowired
    SubscriptionService subscriptions;

    AppUser user;
    AppUser other;
    AppUser admin;

    @BeforeEach
    void setUp() {
        user = save("u@x.com", Role.USER);
        other = save("o@x.com", Role.USER);
        admin = save("a@x.com", Role.ADMIN);
    }

    AppUser save(String email, Role role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setRole(role);
        return users.save(u);
    }

    @Autowired
    ImageFileRepository images;

    long files() {
        return images.count();
    }

    MockMultipartFile img() {
        return new MockMultipartFile("file", "leaf.png", "image/png", new byte[] {1, 2});
    }

    @Test
    void createSetsPendingAndTimestamp() {
        ScanHistory s = service.create(img(), "  note  ", null, user);
        assertThat(s.getStatus()).isEqualTo(ScanStatus.PENDING);
        assertThat(s.getCreatedAt()).isNotNull();
        assertThat(s.getNote()).isEqualTo("note");
        assertThat(images.existsById(s.getImageFileName())).isTrue();
    }

    @Test
    void blankNoteBecomesNull() {
        assertThat(service.create(img(), "   ", null, user).getNote()).isNull();
    }

    @Test
    void deleteRemovesRowAndFile() {
        ScanHistory s = service.create(img(), null, null, user);
        service.delete(s.getId(), user);
        assertThat(repo.findById(s.getId())).isEmpty();
        assertThat(images.existsById(s.getImageFileName())).isFalse();
    }

    @Test
    void deleteWithMissingFileStillRemovesRow() {
        ScanHistory s = service.create(img(), null, null, user);
        images.deleteById(s.getImageFileName());
        service.delete(s.getId(), user);
        assertThat(repo.findById(s.getId())).isEmpty();
    }

    @Test
    void unknownIdIs404() {
        assertThatThrownBy(() -> service.delete(999L, user)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(999L, user)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listClampsToLastPage() {
        for (int i = 0; i < 11; i++) {
            service.create(img(), "n" + i, null, admin);
        }
        var p = service.list(admin, 99);
        assertThat(p.getNumber()).isEqualTo(1);
        assertThat(p.getContent()).hasSize(1);
        assertThat(service.list(admin, -3).getNumber()).isZero();
    }

    @Test
    void listOnEmptyIsPageZero() {
        assertThat(service.list(user, 5).getNumber()).isZero();
    }

    @Test
    void createRecordsOwner() {
        assertThat(service.create(img(), null, null, user).getOwner()).isEqualTo(user);
    }

    @Test
    void freeUserGetsFiveScansThenSixthIsBlockedWithoutWritingAFile() {
        for (int i = 0; i < 5; i++) {
            service.create(img(), null, null, user);
        }
        assertThat(service.remainingToday(user)).isZero();
        long before = files();

        assertThatThrownBy(() -> service.create(img(), null, null, user)).isInstanceOf(ScanLimitException.class);

        assertThat(files()).isEqualTo(before);
        assertThat(repo.countByOwner(user)).isEqualTo(5);
    }

    @Test
    void remainingCountsDown() {
        assertThat(service.remainingToday(user)).isEqualTo(5);
        service.create(img(), null, null, user);
        assertThat(service.remainingToday(user)).isEqualTo(4);
    }

    @Test
    void yesterdaysScansDoNotCount() {
        for (int i = 0; i < 5; i++) {
            ScanHistory s = service.create(img(), null, null, user);
            s.setCreatedAt(LocalDateTime.now().minusDays(1));
            repo.saveAndFlush(s);
        }
        assertThat(service.remainingToday(user)).isEqualTo(5);
        service.create(img(), null, null, user);
    }

    @Test
    void otherUsersScansDoNotUseMyLimit() {
        for (int i = 0; i < 5; i++) {
            service.create(img(), null, null, other);
        }
        assertThat(service.remainingToday(user)).isEqualTo(5);
    }

    @Test
    void premiumAndAdminAreUnlimited() {
        subscriptions.activatePremium(user);
        for (int i = 0; i < 6; i++) {
            service.create(img(), null, null, user);
            service.create(img(), null, null, admin);
        }
        assertThat(service.remainingToday(user)).isNull();
        assertThat(service.remainingToday(admin)).isNull();
    }

    @Test
    void userSeesOnlyOwnScansAdminSeesAllIncludingOwnerless() {
        ScanHistory mine = service.create(img(), null, null, user);
        ScanHistory theirs = service.create(img(), null, null, other);
        ScanHistory old = new ScanHistory();
        old.setImageFileName("old.png");
        old = repo.saveAndFlush(old);

        assertThat(service.list(user, 0).getContent()).containsExactly(mine);
        assertThat(service.list(admin, 0).getContent()).hasSize(3);
        assertThat(service.get(mine.getId(), user)).isEqualTo(mine);
        Long theirsId = theirs.getId();
        Long oldId = old.getId();
        assertThatThrownBy(() -> service.get(theirsId, user)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(oldId, user)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(oldId, admin).getId()).isEqualTo(oldId);
    }

    @Test
    void userCannotDeleteSomeoneElsesScan() {
        ScanHistory theirs = service.create(img(), null, null, other);
        assertThatThrownBy(() -> service.delete(theirs.getId(), user)).isInstanceOf(NotFoundException.class);
        assertThat(repo.findById(theirs.getId())).isPresent();
        assertThat(images.existsById(theirs.getImageFileName())).isTrue();
        service.delete(theirs.getId(), admin);
        assertThat(repo.findById(theirs.getId())).isEmpty();
    }

    Disease tomatoEarlyBlight() {
        Plant p = new Plant();
        p.setName("Cà chua");
        plants.save(p);
        Disease d = new Disease();
        d.setName("Bệnh đốm vòng");
        d.setPlant(p);
        d.setModelLabel("Tomato___Early_blight");
        return diseases.save(d);
    }

    @Test
    void analyzeLinksTheMatchingDiseaseAndItsPlant() {
        Disease d = tomatoEarlyBlight();
        ScanHistory s = service.create(img(), null, null, user);
        when(model.predict(any(), eq(s.getImageFileName())))
                .thenReturn(new ScanClient.Prediction("Tomato___Early_blight", 0.97));

        ScanHistory done = service.analyze(s.getId(), user);

        assertThat(done.getStatus()).isEqualTo(ScanStatus.DONE);
        assertThat(done.getDisease()).isEqualTo(d);
        assertThat(done.getPlant()).isEqualTo(d.getPlant());
        assertThat(done.getConfidence()).isEqualTo(0.97);
        assertThat(done.getModelLabel()).isEqualTo("Tomato___Early_blight");
        verify(model).predict(new byte[] {1, 2}, s.getImageFileName());
    }

    @Test
    void analyzeLogsTheResultSoProductionShowsItWorked(CapturedOutput out) {
        ScanHistory s = service.create(img(), null, null, user);
        when(model.predict(any(), any())).thenReturn(new ScanClient.Prediction("Tomato___Late_blight", 0.6512));

        service.analyze(s.getId(), user);

        assertThat(out.getOut()).containsPattern("Scan " + s.getId()
                + " analysed in \\d+\\.\\ds: Tomato___Late_blight 65% \\(chosen plant: auto\\)");
    }

    @Test
    void analyzeKeepsLabelWhenNoDiseaseMatches() {
        ScanHistory s = service.create(img(), null, null, user);
        when(model.predict(any(), any())).thenReturn(new ScanClient.Prediction("Tomato___healthy", 1.0));

        ScanHistory done = service.analyze(s.getId(), user);

        assertThat(done.getStatus()).isEqualTo(ScanStatus.DONE);
        assertThat(done.getDisease()).isNull();
        assertThat(done.getPlant()).isNull();
        assertThat(done.getModelLabel()).isEqualTo("Tomato___healthy");
    }

    @Test
    void analyzeMarksFailedWhenTheModelServiceFails() {
        ScanHistory s = service.create(img(), null, null, user);
        when(model.predict(any(), any())).thenThrow(new ResourceAccessException("timeout"));

        ScanHistory failed = service.analyze(s.getId(), user);

        assertThat(failed.getStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(failed.getModelLabel()).isNull();
        assertThat(repo.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(ScanStatus.FAILED);
    }

    @Test
    void analyzeAfterFailureCanSucceed() {
        ScanHistory s = service.create(img(), null, null, user);
        when(model.predict(any(), any())).thenThrow(new ResourceAccessException("down"))
                .thenReturn(new ScanClient.Prediction("Background_without_leaves", 1.0));
        service.analyze(s.getId(), user);
        assertThat(service.analyze(s.getId(), user).getStatus()).isEqualTo(ScanStatus.DONE);
    }

    @Test
    void analyzeWithMissingImageFailsWithoutCallingTheModel() {
        ScanHistory s = service.create(img(), null, null, user);
        images.deleteById(s.getImageFileName());

        assertThat(service.analyze(s.getId(), user).getStatus()).isEqualTo(ScanStatus.FAILED);
        verifyNoInteractions(model);
    }

    @Test
    void userCannotAnalyzeSomeoneElsesScan() {
        ScanHistory theirs = service.create(img(), null, null, other);
        Long id = theirs.getId();
        assertThatThrownBy(() -> service.analyze(id, user)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(model);
    }

    Plant plant(String name, String prefix) {
        Plant p = new Plant();
        p.setName(name);
        p.setModelPrefix(prefix);
        return plants.save(p);
    }

    static Map<String, Double> probs(Object... labelAndProb) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < labelAndProb.length; i += 2) {
            m.put((String) labelAndProb[i], (Double) labelAndProb[i + 1]);
        }
        return m;
    }

    @Test
    void createRemembersTheChosenPlant() {
        Plant tomato = plant("Cà chua", "Tomato");
        assertThat(service.create(img(), null, tomato.getId(), user).getChosenPlant()).isEqualTo(tomato);
    }

    @Test
    void createRejectsAPlantTheModelDoesNotKnowWithoutStoringAFile() {
        Plant rose = plant("Hoa hồng", null);
        assertThatThrownBy(() -> service.create(img(), null, rose.getId(), user)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.create(img(), null, 999L, user)).isInstanceOf(NotFoundException.class);
        assertThat(files()).isZero();
    }

    @Test
    void scannablePlantsAreTheOnesWithAModelPrefix() {
        plant("Hoa hồng", null);
        plant("Táo", "Apple");
        assertThat(service.scannablePlants()).extracting(Plant::getName).containsExactly("Táo");
    }

    @Test
    void withoutAChosenPlantTheBestOfAllLabelsWinsAndTop3IsStored() {
        tomatoEarlyBlight();
        Plant grape = plant("Nho", "Grape");
        ScanHistory s = service.create(img(), null, null, user);
        when(model.predict(any(), any())).thenReturn(new ScanClient.Prediction("Grape___healthy", 0.5, probs(
                "Grape___healthy", 0.5, "Tomato___Early_blight", 0.3, "Tomato___Late_blight", 0.15, "Apple___healthy", 0.05)));

        ScanHistory done = service.analyze(s.getId(), user);

        assertThat(done.getModelLabel()).isEqualTo("Grape___healthy");
        assertThat(done.getConfidence()).isEqualTo(0.5);
        assertThat(done.getDisease()).isNull();
        assertThat(done.getPlant()).isEqualTo(grape); // plant comes from the label even for a healthy leaf
        assertThat(done.getLooksLikePlant()).isNull();
        assertThat(done.getTop3()).isEqualTo("Grape___healthy=0.5000;Tomato___Early_blight=0.3000;Tomato___Late_blight=0.1500");
    }

    @Test
    void aChosenPlantLimitsTheResultToItsLabelsRenormalised() {
        Disease blight = tomatoEarlyBlight();
        Plant tomato = blight.getPlant();
        tomato.setModelPrefix("Tomato");
        plants.save(tomato);
        plant("Nho", "Grape");
        ScanHistory s = service.create(img(), null, tomato.getId(), user);
        when(model.predict(any(), any())).thenReturn(new ScanClient.Prediction("Grape___healthy", 0.4, probs(
                "Grape___healthy", 0.4, "Tomato___Early_blight", 0.45, "Tomato___Late_blight", 0.15)));

        ScanHistory done = service.analyze(s.getId(), user);

        assertThat(done.getModelLabel()).isEqualTo("Tomato___Early_blight");
        assertThat(done.getConfidence()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(done.getDisease()).isEqualTo(blight);
        assertThat(done.getPlant()).isEqualTo(tomato);
        assertThat(done.getLooksLikePlant()).isNull(); // grape was the top guess, but under 50%
        assertThat(done.getTop3()).isEqualTo("Tomato___Early_blight=0.7500;Tomato___Late_blight=0.2500");
    }

    @Test
    void aPhotoClearlyOfAnotherPlantIsReported() {
        Plant tomato = plant("Cà chua", "Tomato");
        Plant grape = plant("Nho", "Grape");
        ScanHistory s = service.create(img(), null, tomato.getId(), user);
        when(model.predict(any(), any())).thenReturn(new ScanClient.Prediction("Grape___Black_rot", 0.9, probs(
                "Grape___Black_rot", 0.9, "Tomato___healthy", 0.06, "Tomato___Leaf_Mold", 0.04)));

        ScanHistory done = service.analyze(s.getId(), user);

        assertThat(done.getModelLabel()).isEqualTo("Tomato___healthy");
        assertThat(done.getLooksLikePlant()).isEqualTo(grape);
    }

    @Test
    void noLeafWinsEvenWithAChosenPlant() {
        Plant tomato = plant("Cà chua", "Tomato");
        ScanHistory s = service.create(img(), null, tomato.getId(), user);
        when(model.predict(any(), any())).thenReturn(new ScanClient.Prediction("Background_without_leaves", 0.8, probs(
                "Background_without_leaves", 0.8, "Tomato___healthy", 0.2)));

        ScanHistory done = service.analyze(s.getId(), user);

        assertThat(done.getModelLabel()).isEqualTo("Background_without_leaves");
        assertThat(done.getPlant()).isNull();
        assertThat(done.getLooksLikePlant()).isNull();
    }

    @Test
    void guessesUseVietnameseNames() {
        tomatoEarlyBlight().getPlant().setModelPrefix("Tomato");
        plant("Nho", "Grape");
        ScanHistory s = new ScanHistory();
        s.setTop3("Tomato___Early_blight=0.6000;Grape___healthy=0.3000;Background_without_leaves=0.0500;X___y=0.0500");

        assertThat(service.guesses(s)).containsExactly(
                new ScanHistoryService.Guess("Bệnh đốm vòng", "Cà chua", 0.6),
                new ScanHistoryService.Guess("Lá khoẻ", "Nho", 0.3),
                new ScanHistoryService.Guess("Không thấy lá", null, 0.05),
                new ScanHistoryService.Guess("X___y", null, 0.05));
        assertThat(service.guesses(new ScanHistory())).isEmpty();
    }
}
