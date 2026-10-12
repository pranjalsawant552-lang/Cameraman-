package com.cameraman.attendance.controller;

import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.service.AttendanceService;
import com.cameraman.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (no-auth-header) attendance ingestion endpoints — reachable under /webhooks/**,
 * which SecurityConfig already exempts from JWT auth, since the devices/scripts hitting
 * these can't carry a staff login session. Authentication here is instead a shared secret
 * checked inside AttendanceService (see verifyWebhookKey) — every request must supply the
 * key generated on the Attendance settings screen.
 *
 * Two integration paths live here:
 *  - /generic:      a plain JSON POST. Works with literally any device, bridge script, or
 *                    automation tool that can make an HTTP request — the true "any machine"
 *                    answer for devices with no built-in server-push feature.
 *  - /iclock/**:     the ZKTeco "push SDK" / ADMS protocol subset. A large share of budget
 *                    biometric/RFID attendance machines sold in India speak this natively
 *                    (ZKTeco, eSSL, Realtime, Mantra, and many rebadged devices built on the
 *                    same reference firmware) — pointing the device's "Cloud/ADMS Server"
 *                    setting at this URL means it starts pushing automatically, with zero
 *                    per-model code on our side.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance Webhooks", description = "Device-facing attendance ingestion — no staff login required, shared-secret authenticated")
public class AttendanceWebhookController {

    private final AttendanceService attendanceService;

    @PostMapping("/generic")
    public ResponseEntity<ApiResponse<Void>> generic(@Valid @RequestBody AttendanceDto.GenericWebhookRequest request) {
        attendanceService.ingestGenericWebhook(request);
        return ResponseEntity.ok(ApiResponse.success("Punch recorded", null));
    }

    // ── ZKTeco / ADMS protocol subset ──
    // Devices call these three endpoints in this order: cdata(GET) to register, then
    // repeatedly cdata(POST) to upload logs and getrequest(GET) to poll for commands.
    // Responses must be plain text in the exact format the firmware expects — not JSON.

    @GetMapping(value = "/iclock/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handshake(@RequestParam(required = false) String SN,
                                             @RequestParam(required = false) String key) {
        attendanceService.verifyDeviceRequest(SN, key);
        log.info("Attendance device registered: SN={}", SN);
        // Minimal handshake reply a device expects on GET cdata: registration ack plus
        // polling interval (seconds). No further device-side config is pushed from here.
        return ResponseEntity.ok("GET OPTION FROM: " + (SN != null ? SN : "unknown") + "\nATTLOGStamp=None\nOPERLOGStamp=None\nATTPHOTOStamp=None\nErrorDelay=30\nDelay=30\nTransFlag=1111000000\nRealtime=1\nEncrypt=None");
    }

    @PostMapping(value = "/iclock/cdata", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> uploadLog(@RequestParam(required = false) String SN,
                                             @RequestParam(required = false) String key,
                                             @RequestParam(required = false) String table,
                                             @RequestBody(required = false) String body) {
        attendanceService.verifyDeviceRequest(SN, key);
        // Devices also upload operation logs (OPERLOG), user records etc. to this same URL.
        // Only the ATTLOG table holds punches; acknowledge everything else without parsing.
        if (table != null && !table.isBlank() && !"ATTLOG".equalsIgnoreCase(table)) {
            return ResponseEntity.ok("OK: 0");
        }
        int count = attendanceService.ingestZkAttendanceLog(SN, body);
        log.info("Attendance device {} pushed {} record(s) (table={})", SN, count, table);
        return ResponseEntity.ok("OK: " + count);
    }

    @GetMapping(value = "/iclock/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> pollCommands(@RequestParam(required = false) String SN,
                                                @RequestParam(required = false) String key) {
        attendanceService.verifyDeviceRequest(SN, key);
        // No remote-command queue on our side (we only ingest, never push config back to
        // the device) — "OK" tells the device there's nothing pending this cycle.
        return ResponseEntity.ok("OK");
    }
}
