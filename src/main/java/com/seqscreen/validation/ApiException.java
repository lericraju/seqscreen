package com.seqscreen.validation;

/** A rejected request. The status is returned as-is so the UI can explain what went wrong. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException noDataset() {
        return new ApiException(409, "No dataset is loaded. Generate one or upload a FASTA file first.");
    }
}
