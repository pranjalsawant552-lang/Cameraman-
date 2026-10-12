package com.cameraman.outdoor.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.outdoor.domain.OutdoorShoot;
import com.cameraman.outdoor.domain.OutdoorShootCategory;
import com.cameraman.outdoor.domain.OutdoorShootStatus;
import com.cameraman.outdoor.dto.OutdoorShootDto;
import com.cameraman.outdoor.repository.OutdoorShootRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OutdoorShootService {

    private final OutdoorShootRepository outdoorShootRepository;
    private final UserRepository userRepository;
    private final com.cameraman.billing.service.AutoInvoiceService autoInvoiceService;

    public OutdoorShootDto.OutdoorShootResponse create(OutdoorShootDto.CreateRequest request) {
        return create(request, null);
    }

    public OutdoorShootDto.OutdoorShootResponse create(OutdoorShootDto.CreateRequest request, String createdByUserId) {
        User client = resolveUser(request.getClientId(), "Client");
        User photographer = resolveUser(request.getPhotographerId(), "Photographer");
        User editor = resolveUser(request.getEditorId(), "Editor");

        if (photographer != null) {
            boolean conflict = !outdoorShootRepository
                    .findByPhotographerAndDate(photographer.getId(), request.getShootDate())
                    .isEmpty();
            if (conflict) {
                throw new BusinessException("This photographer already has an outdoor shoot on that date", "PHOTOGRAPHER_UNAVAILABLE");
            }
        }

        OutdoorShoot shoot = OutdoorShoot.builder()
                .shootNumber(generateShootNumber())
                .client(client).clientName(request.getClientName())
                .clientMobile(request.getClientMobile()).clientEmail(request.getClientEmail())
                .category(OutdoorShootCategory.valueOf(request.getCategory()))
                .packageId(request.getPackageId()).packageName(request.getPackageName())
                .shootDate(request.getShootDate()).endDate(request.getEndDate())
                .startTime(request.getStartTime()).endTime(request.getEndTime())
                .venueName(request.getVenueName()).venueAddress(request.getVenueAddress())
                .venueCity(request.getVenueCity()).venuePincode(request.getVenuePincode())
                .travelDistanceKm(request.getTravelDistanceKm())
                .travelCost(request.getTravelCost() != null ? request.getTravelCost() : java.math.BigDecimal.ZERO)
                .weatherDependent(request.isWeatherDependent()).backupPlanNotes(request.getBackupPlanNotes())
                .equipmentChecklist(request.getEquipmentChecklist())
                .photographer(photographer).editor(editor)
                .status(OutdoorShootStatus.CONFIRMED) // staff-entered, so confirmed from the start
                .totalAmount(request.getTotalAmount()).advanceAmount(request.getAdvanceAmount())
                .notes(request.getNotes())
                .build();

        shoot = outdoorShootRepository.save(shoot);
        // Every shoot gets an invoice in the Invoice module (after this transaction commits).
        autoInvoiceService.onOutdoorShootCreated(shoot.getId(), createdByUserId);
        return toResponse(shoot);
    }

    public OutdoorShootDto.OutdoorShootResponse update(String id, OutdoorShootDto.UpdateRequest request) {
        OutdoorShoot shoot = findById(id);
        if (request.getClientName() != null) shoot.setClientName(request.getClientName());
        if (request.getClientMobile() != null) shoot.setClientMobile(request.getClientMobile());
        if (request.getClientEmail() != null) shoot.setClientEmail(request.getClientEmail());
        if (request.getCategory() != null) shoot.setCategory(OutdoorShootCategory.valueOf(request.getCategory()));
        if (request.getPackageId() != null) shoot.setPackageId(request.getPackageId());
        if (request.getPackageName() != null) shoot.setPackageName(request.getPackageName());
        if (request.getShootDate() != null) shoot.setShootDate(request.getShootDate());
        if (request.getEndDate() != null) shoot.setEndDate(request.getEndDate());
        if (request.getStartTime() != null) shoot.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) shoot.setEndTime(request.getEndTime());
        if (request.getVenueName() != null) shoot.setVenueName(request.getVenueName());
        if (request.getVenueAddress() != null) shoot.setVenueAddress(request.getVenueAddress());
        if (request.getVenueCity() != null) shoot.setVenueCity(request.getVenueCity());
        if (request.getVenuePincode() != null) shoot.setVenuePincode(request.getVenuePincode());
        if (request.getTravelDistanceKm() != null) shoot.setTravelDistanceKm(request.getTravelDistanceKm());
        if (request.getTravelCost() != null) shoot.setTravelCost(request.getTravelCost());
        if (request.getWeatherDependent() != null) shoot.setWeatherDependent(request.getWeatherDependent());
        if (request.getBackupPlanNotes() != null) shoot.setBackupPlanNotes(request.getBackupPlanNotes());
        if (request.getEquipmentChecklist() != null) shoot.setEquipmentChecklist(request.getEquipmentChecklist());
        if (request.getTotalAmount() != null) shoot.setTotalAmount(request.getTotalAmount());
        if (request.getAdvanceAmount() != null) shoot.setAdvanceAmount(request.getAdvanceAmount());
        if (request.getNotes() != null) shoot.setNotes(request.getNotes());
        if (request.getStatus() != null) shoot.setStatus(OutdoorShootStatus.valueOf(request.getStatus()));
        if (request.getPhotographerId() != null && !request.getPhotographerId().isBlank()) {
            shoot.setPhotographer(resolveUser(request.getPhotographerId(), "Photographer"));
        }
        if (request.getEditorId() != null && !request.getEditorId().isBlank()) {
            shoot.setEditor(resolveUser(request.getEditorId(), "Editor"));
        }
        return toResponse(outdoorShootRepository.save(shoot));
    }

    public OutdoorShootDto.OutdoorShootResponse reschedule(String id, OutdoorShootDto.RescheduleRequest request) {
        OutdoorShoot shoot = findById(id);
        if (shoot.getStatus() == OutdoorShootStatus.CANCELLED) {
            throw new BusinessException("Cannot reschedule a cancelled shoot", "SHOOT_CANCELLED");
        }
        shoot.setShootDate(request.getNewDate());
        shoot.setEndDate(request.getNewEndDate());
        shoot.setStartTime(request.getNewStartTime());
        shoot.setEndTime(request.getNewEndTime());
        shoot.setStatus(OutdoorShootStatus.RESCHEDULED);
        shoot.setRescheduledCount(shoot.getRescheduledCount() + 1);
        return toResponse(outdoorShootRepository.save(shoot));
    }

    public OutdoorShootDto.OutdoorShootResponse cancel(String id, OutdoorShootDto.CancelRequest request) {
        OutdoorShoot shoot = findById(id);
        if (shoot.getStatus() == OutdoorShootStatus.COMPLETED) {
            throw new BusinessException("Cannot cancel a completed shoot", "SHOOT_COMPLETED");
        }
        shoot.setStatus(OutdoorShootStatus.CANCELLED);
        shoot.setCancelledReason(request.getReason());
        shoot = outdoorShootRepository.save(shoot);
        autoInvoiceService.onSourceCancelled(shoot.getId(), request.getReason());
        return toResponse(shoot);
    }

    @Transactional(readOnly = true)
    public OutdoorShootDto.OutdoorShootResponse getById(String id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<OutdoorShootDto.OutdoorShootResponse> search(String status, String category, String search, Pageable pageable) {
        OutdoorShootStatus statusEnum = (status != null && !status.isBlank()) ? OutdoorShootStatus.valueOf(status) : null;
        OutdoorShootCategory categoryEnum = (category != null && !category.isBlank()) ? OutdoorShootCategory.valueOf(category) : null;
        String q = (search != null && !search.isBlank()) ? search : null;
        Page<OutdoorShoot> page = outdoorShootRepository.search(statusEnum, categoryEnum, q, pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<OutdoorShootDto.OutdoorShootResponse> getByDateRange(LocalDate from, LocalDate to) {
        return outdoorShootRepository.findByShootDateBetween(from, to).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    private User resolveUser(String id, String label) {
        if (id == null || id.isBlank()) return null;
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(label, "id", id));
    }

    private String generateShootNumber() {
        String prefix = "OD" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        long countThisMonth = outdoorShootRepository.count() + 1;
        return prefix + String.format("%04d", countThisMonth);
    }

    private OutdoorShoot findById(String id) {
        return outdoorShootRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outdoor Shoot", "id", id));
    }

    private OutdoorShootDto.OutdoorShootResponse toResponse(OutdoorShoot s) {
        return OutdoorShootDto.OutdoorShootResponse.builder()
                .id(s.getId()).shootNumber(s.getShootNumber())
                .clientId(s.getClient() != null ? s.getClient().getId() : null)
                .clientName(s.getClientName()).clientMobile(s.getClientMobile()).clientEmail(s.getClientEmail())
                .category(s.getCategory().name())
                .packageId(s.getPackageId()).packageName(s.getPackageName())
                .shootDate(s.getShootDate()).endDate(s.getEndDate())
                .startTime(s.getStartTime()).endTime(s.getEndTime())
                .venueName(s.getVenueName()).venueAddress(s.getVenueAddress())
                .venueCity(s.getVenueCity()).venuePincode(s.getVenuePincode())
                .travelDistanceKm(s.getTravelDistanceKm()).travelCost(s.getTravelCost())
                .weatherDependent(s.isWeatherDependent()).backupPlanNotes(s.getBackupPlanNotes())
                .equipmentChecklist(s.getEquipmentChecklist())
                .photographerId(s.getPhotographer() != null ? s.getPhotographer().getId() : null)
                .photographerName(s.getPhotographer() != null ? s.getPhotographer().getFullName() : null)
                .editorId(s.getEditor() != null ? s.getEditor().getId() : null)
                .editorName(s.getEditor() != null ? s.getEditor().getFullName() : null)
                .status(s.getStatus().name())
                .totalAmount(s.getTotalAmount()).advanceAmount(s.getAdvanceAmount()).balanceAmount(s.getBalanceAmount())
                .notes(s.getNotes()).cancelledReason(s.getCancelledReason()).rescheduledCount(s.getRescheduledCount())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }
}
