package com.cameraman.storage.provider;

import com.cameraman.common.exception.StorageException;

/** A resumable upload session no longer exists at the provider; the file must be re-uploaded. */
public class UploadSessionExpiredException extends StorageException {
    public UploadSessionExpiredException(String message) {
        super(message);
    }
}
