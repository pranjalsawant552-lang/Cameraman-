package com.cameraman.storage.provider;

/**
 * Optional capability: chunked, resumable uploads relayed through the backend. Used for providers
 * (Google Drive) that cannot hand the browser a presigned PUT URL without exposing the account.
 * The browser sends fixed-size chunks to the app; the app forwards each to the provider session.
 * The session URI is a bearer secret and must never be sent to a client.
 */
public interface ResumableUploads {

    /** Every chunk except the last must be a multiple of this (Google requires 256 KiB). */
    int CHUNK_ALIGNMENT = 256 * 1024;

    record Session(String key, String sessionUri) {}

    /** @param received bytes the provider has durably received so far; {@code complete} when done */
    record Progress(boolean complete, long received) {}

    Session beginResumable(String folder, String filename, String contentType, long totalSize);

    Progress uploadChunk(String sessionUri, long offset, byte[] chunk, long totalSize);

    /** Asks the provider how many bytes it already has, so an interrupted upload can resume. */
    Progress queryProgress(String sessionUri, long totalSize);
}
