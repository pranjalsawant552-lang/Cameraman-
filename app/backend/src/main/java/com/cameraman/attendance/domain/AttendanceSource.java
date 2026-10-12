package com.cameraman.attendance.domain;

public enum AttendanceSource {
    /** Typed in by staff — the universal fallback that works regardless of
     *  what hardware (or paper register) is in use. */
    MANUAL,
    /** Bulk-imported from a CSV/Excel export — nearly every attendance
     *  machine's bundled software can produce this, regardless of brand. */
    CSV_IMPORT,
    /** A plain HTTP POST of JSON to /webhooks/attendance/generic — works for
     *  any device or bridge script that can make an outbound web request. */
    GENERIC_WEBHOOK,
    /** The ZKTeco "push SDK" / ADMS protocol
     *  (/webhooks/attendance/iclock/**) — a de-facto standard that a large
     *  share of budget biometric/RFID machines sold in India speak natively
     *  (ZKTeco, eSSL, Realtime, Mantra, and many OEM/rebadged devices built
     *  on the same reference firmware), so this one integration covers many
     *  models without per-model code. */
    ZK_PROTOCOL
}
