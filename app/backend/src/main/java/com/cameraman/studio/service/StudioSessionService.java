package com.cameraman.studio.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.studio.domain.StudioSession;
import com.cameraman.studio.domain.StudioSessionStatus;
import com.cameraman.studio.dto.StudioSessionDto;
import com.cameraman.studio.repository.StudioSessionRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StudioSessionService {

    private final StudioSessionRepository studioSessionRepository;
    private final UserRepository userRepository;
    private final com.cameraman.billing.service.AutoInvoiceService autoInvoiceService;

    public StudioSessionDto.StudioSessionResponse create(StudioSessionDto.CreateRequest request) {
        return create(request, null);
    }

    public StudioSessionDto.StudioSessionResponse create(StudioSessionDto.CreateRequest request, String createdByUserId) {
        User client = null;
        if (request.getClientId() != null && !request.getClientId().isBlank()) {
            client = userRepository.findById(request.getClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client", "id", request.getClientId()));
        }
        User photographer = null;
        if (request.getPhotographerId() != null && !request.getPhotographerId().isBlank()) {
            photographer = userRepository.findById(request.getPhotographerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Photographer", "id", request.getPhotographerId()));
        }
        User editor = null;
        if (request.getEditorId() != null && !request.getEditorId().isBlank()) {
            editor = userRepository.findById(request.getEditorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Editor", "id", request.getEditorId()));
        }

        // Room-conflict check: same room, same date, overlapping time — only when a
        // room is actually specified (unassigned/flexible sessions never conflict).
        if (request.getStudioRoom() != null && !request.getStudioRoom().isBlank()) {
            LocalTime newStart = request.getStartTime();
            LocalTime newEnd = request.getEndTime() != null ? request.getEndTime() : newStart.plusHours(1);
            boolean conflict = studioSessionRepository.findBySessionDate(request.getSessionDate()).stream()
                    .filter(s -> s.getStatus() != StudioSessionStatus.CANCELLED)
                    .filter(s -> request.getStudioRoom().equals(s.getStudioRoom()))
                    .anyMatch(s -> overlaps(s, newStart, newEnd));
            if (conflict) {
                throw new BusinessException("That studio room is already booked for an overlapping time on this date", "ROOM_UNAVAILABLE");
            }
        }

        StudioSession session = StudioSession.builder()
                .sessionNumber(generateSessionNumber())
                .client(client).clientName(request.getClientName())
                .clientMobile(request.getClientMobile()).clientEmail(request.getClientEmail())
                .packageId(request.getPackageId()).packageName(request.getPackageName())
                .sessionDate(request.getSessionDate()).startTime(request.getStartTime()).endTime(request.getEndTime())
                .studioRoom(request.getStudioRoom()).backdropNotes(request.getBackdropNotes())
                .propsRequired(request.getPropsRequired())
                .photographer(photographer).editor(editor)
                .status(StudioSessionStatus.PENDING)
                .totalAmount(request.getTotalAmount()).advanceAmount(request.getAdvanceAmount())
                .notes(request.getNotes())
                .build();

        session = studioSessionRepository.save(session);
        // Every session gets an invoice in the Invoice module (after this transaction commits).
        autoInvoiceService.onStudioSessionCreated(session.getId(), createdByUserId);
        return toResponse(session);
    }

    public StudioSessionDto.StudioSessionResponse update(String id, StudioSessionDto.UpdateRequest request) {
        StudioSession session = findById(id);
        if (request.getClientName() != null) session.setClientName(request.getClientName());
        if (request.getClientMobile() != null) session.setClientMobile(request.getClientMobile());
        if (request.getClientEmail() != null) session.setClientEmail(request.getClientEmail());
        if (request.getPackageId() != null) session.setPackageId(request.getPackageId());
        if (request.getPackageName() != null) session.setPackageName(request.getPackageName());
        if (request.getSessionDate() != null) session.setSessionDate(request.getSessionDate());
        if (request.getStartTime() != null) session.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) session.setEndTime(request.getEndTime());
        if (request.getStudioRoom() != null) session.setStudioRoom(request.getStudioRoom());
        if (request.getBackdropNotes() != null) session.setBackdropNotes(request.getBackdropNotes());
        if (request.getPropsRequired() != null) session.setPropsRequired(request.getPropsRequired());
        if (request.getTotalAmount() != null) session.setTotalAmount(request.getTotalAmount());
        if (request.getAdvanceAmount() != null) session.setAdvanceAmount(request.getAdvanceAmount());
        if (request.getNotes() != null) session.setNotes(request.getNotes());
        if (request.getStatus() != null) session.setStatus(StudioSessionStatus.valueOf(request.getStatus()));

        if (request.getPhotographerId() != null && !request.getPhotographerId().isBlank()) {
            session.setPhotographer(userRepository.findById(request.getPhotographerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Photographer", "id", request.getPhotographerId())));
        }
        if (request.getEditorId() != null && !request.getEditorId().isBlank()) {
            session.setEditor(userRepository.findById(request.getEditorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Editor", "id", request.getEditorId())));
        }

        return toResponse(studioSessionRepository.save(session));
    }

    public StudioSessionDto.StudioSessionResponse reschedule(String id, StudioSessionDto.RescheduleRequest request) {
        StudioSession session = findById(id);
        if (session.getStatus() == StudioSessionStatus.CANCELLED) {
            throw new BusinessException("Cannot reschedule a cancelled session", "SESSION_CANCELLED");
        }
        session.setSessionDate(request.getNewDate());
        session.setStartTime(request.getNewStartTime());
        session.setEndTime(request.getNewEndTime());
        session.setStatus(StudioSessionStatus.RESCHEDULED);
        session.setRescheduledCount(session.getRescheduledCount() + 1);
        return toResponse(studioSessionRepository.save(session));
    }

    public StudioSessionDto.StudioSessionResponse cancel(String id, StudioSessionDto.CancelRequest request) {
        StudioSession session = findById(id);
        if (session.getStatus() == StudioSessionStatus.COMPLETED) {
            throw new BusinessException("Cannot cancel a completed session", "SESSION_COMPLETED");
        }
        session.setStatus(StudioSessionStatus.CANCELLED);
        session.setCancelledReason(request.getReason());
        return toResponse(studioSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public StudioSessionDto.StudioSessionResponse getById(String id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<StudioSessionDto.StudioSessionResponse> search(String status, String studioRoom, String search, Pageable pageable) {
        StudioSessionStatus statusEnum = (status != null && !status.isBlank()) ? StudioSessionStatus.valueOf(status) : null;
        String room = (studioRoom != null && !studioRoom.isBlank()) ? studioRoom : null;
        String q = (search != null && !search.isBlank()) ? search : null;
        Page<StudioSession> page = studioSessionRepository.search(statusEnum, room, q, pageable);
        return PagedResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<StudioSessionDto.StudioSessionResponse> getByDateRange(LocalDate from, LocalDate to) {
        return studioSessionRepository.findBySessionDateBetween(from, to).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudioSessionDto.SlotAvailability> checkAvailability(
            StudioSessionDto.AvailabilityRequest request) {

        List<StudioSession> existing = studioSessionRepository
                .findBySessionDate(request.getDate())
                .stream()
                .filter(s -> s.getStatus() != StudioSessionStatus.CANCELLED)
                .filter(s -> request.getStudioRoom() == null
                        || request.getStudioRoom().isBlank()
                        || request.getStudioRoom().equals(s.getStudioRoom()))
                .collect(Collectors.toList());

        List<StudioSessionDto.SlotAvailability> slots = new ArrayList<>();

        LocalTime currentTime = LocalTime.of(9, 0);
        LocalTime closingTime = LocalTime.of(19, 0);

        while (currentTime.isBefore(closingTime)) {

            final LocalTime slotStart = currentTime;
            final LocalTime slotEnd = currentTime.plusHours(1);

            StudioSession conflict = existing.stream()
                    .filter(sess -> overlaps(sess, slotStart, slotEnd))
                    .findFirst()
                    .orElse(null);

            slots.add(
                    StudioSessionDto.SlotAvailability.builder()
                            .startTime(slotStart)
                            .endTime(slotEnd)
                            .available(conflict == null)
                            .bookedBy(
                                    conflict != null
                                            ? conflict.getClientName()
                                            : null
                            )
                            .build()
            );

            currentTime = slotEnd;
        }

        return slots;
    }

    private boolean overlaps(StudioSession session, LocalTime newStart, LocalTime newEnd) {
        LocalTime existingStart = session.getStartTime();
        LocalTime existingEnd = session.getEndTime() != null ? session.getEndTime() : existingStart.plusHours(1);
        return newStart.isBefore(existingEnd) && existingStart.isBefore(newEnd);
    }

    private String generateSessionNumber() {
        String prefix = "ST" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        long countThisMonth = studioSessionRepository.count() + 1;
        return prefix + String.format("%04d", countThisMonth);
    }

    private StudioSession findById(String id) {
        return studioSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Studio Session", "id", id));
    }

    private StudioSessionDto.StudioSessionResponse toResponse(StudioSession s) {
        return StudioSessionDto.StudioSessionResponse.builder()
                .id(s.getId()).sessionNumber(s.getSessionNumber())
                .clientId(s.getClient() != null ? s.getClient().getId() : null)
                .clientName(s.getClientName()).clientMobile(s.getClientMobile()).clientEmail(s.getClientEmail())
                .packageId(s.getPackageId()).packageName(s.getPackageName())
                .sessionDate(s.getSessionDate()).startTime(s.getStartTime()).endTime(s.getEndTime())
                .studioRoom(s.getStudioRoom()).backdropNotes(s.getBackdropNotes()).propsRequired(s.getPropsRequired())
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
