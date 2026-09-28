package com.example.nutmeg.api.room;

/** An expected, user-facing failure. The code is for the client, the message is for the human. */
public class RoomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public RoomException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
