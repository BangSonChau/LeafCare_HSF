package com.son.leafcare.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.son.leafcare.common.DuplicateNameException;
import com.son.leafcare.common.InUseException;
import com.son.leafcare.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class PlantServiceTest {

    PlantRepository repo = mock(PlantRepository.class);
    PlantService service = new PlantService(repo);

    Plant plant(Long id, String name) {
        Plant p = new Plant();
        p.setId(id);
        p.setName(name);
        return p;
    }

    @Test
    void searchTrimsQueryAndSortsByNameSize10() {
        when(repo.findByNameContainingIgnoreCase(any(), any())).thenReturn(Page.empty());
        service.search("  lan ", 0);
        var pc = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByNameContainingIgnoreCase(eq("lan"), pc.capture());
        assertThat(pc.getValue().getPageSize()).isEqualTo(10);
        assertThat(pc.getValue().getSort().getOrderFor("name")).isNotNull();
    }

    @Test
    void searchBlankAndNegativePage() {
        when(repo.findByNameContainingIgnoreCase(any(), any())).thenReturn(Page.empty());
        service.search("   ", -5);
        var pc = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByNameContainingIgnoreCase(eq(""), pc.capture());
        assertThat(pc.getValue().getPageNumber()).isZero();
    }

    @Test
    void searchClampsTooLargePageToLast() {
        when(repo.findByNameContainingIgnoreCase(any(), any())).thenAnswer(inv -> {
            Pageable p = inv.getArgument(1);
            return new PageImpl<>(p.getPageNumber() > 2 ? List.<Plant>of() : List.of(plant(1L, "a")), p, 25);
        });
        assertThat(service.search("", 99).getNumber()).isEqualTo(2);
    }

    @Test
    void getUnknownThrowsNotFound() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void saveNewDuplicateThrows() {
        when(repo.existsByNameIgnoreCase("Lan")).thenReturn(true);
        assertThatThrownBy(() -> service.save(plant(null, " Lan "))).isInstanceOf(DuplicateNameException.class);
    }

    @Test
    void saveEditKeepingOwnNameIsAllowed() {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.save(plant(1L, "Lan")).getName()).isEqualTo("Lan");
    }

    @Test
    void saveEditToOtherPlantsNameThrows() {
        when(repo.existsByNameIgnoreCaseAndIdNot("lan", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.save(plant(1L, "lan"))).isInstanceOf(DuplicateNameException.class);
    }

    @Test
    void deleteReferencedThrowsInUse() {
        when(repo.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("fk")).when(repo).flush();
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(InUseException.class);
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        when(repo.existsById(1L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(NotFoundException.class);
    }
}
