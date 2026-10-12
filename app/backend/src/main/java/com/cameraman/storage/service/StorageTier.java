package com.cameraman.storage.service;

/**
 * What a stored object is FOR. Routing is decided per tier (see {@link StorageService}), which is
 * what lets the studio keep the big, rarely-read originals on cheap Google Drive space while the
 * small, frequently-read files stay on S3 where signed URLs and CDN delivery work natively.
 */
public enum StorageTier {
    /** Full-resolution camera originals. Large, written once, read rarely. Drive-friendly. */
    ORIGINALS,
    /** Thumbnails / previews / watermarked previews. Small, read constantly by the browser. */
    DERIVATIVES,
    /** Generated documents the app itself re-reads (invoice PDFs). */
    DOCUMENTS,
    /** Anything that must load with no auth in a plain {@code <img>} or be fetched by a third
     *  party (CMS images, inventory QR codes, WhatsApp broadcast media). Always S3. */
    PUBLIC;

    /** Tier implied by a legacy folder name, so existing callers keep working unchanged. */
    public static StorageTier forFolder(String folder) {
        if (folder == null) return PUBLIC;
        if (folder.endsWith("/originals")) return ORIGINALS;
        if (folder.endsWith("/thumbnails") || folder.endsWith("/previews") || folder.endsWith("/watermarked")) {
            return DERIVATIVES;
        }
        if (folder.equals("invoices") || folder.startsWith("invoices/")) return DOCUMENTS;
        return PUBLIC;
    }
}
