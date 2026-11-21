package io.github.bags156.hyundailock;

/** A failure worth showing to the user as-is. */
final class BluelinkException extends Exception {

    private static final long serialVersionUID = 1L;

    /** HTTP status or Hyundai error code, when there is one. */
    final String code;

    BluelinkException(String message) {
        this(message, null);
    }

    BluelinkException(String message, String code) {
        super(message);
        this.code = code;
    }
}
