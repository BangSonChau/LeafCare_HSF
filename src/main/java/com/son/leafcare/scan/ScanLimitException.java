package com.son.leafcare.scan;

/** A Free account already used its scans for today. */
public class ScanLimitException extends RuntimeException {

    public ScanLimitException() {
        super("Tài khoản Free được quét 5 lượt/ngày. Nâng cấp Premium để quét không giới hạn.");
    }
}
