package com.cameraman.booking.domain;

public enum LocationType {
    INDOOR, OUTDOOR;

    public static LocationType parse(String raw) {
        if (raw == null || raw.isBlank()) return INDOOR;
        try {
            return LocationType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.cameraman.common.exception.BusinessException("locationType must be INDOOR or OUTDOOR", "INVALID_LOCATION_TYPE");
        }
    }
}
