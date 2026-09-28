package com.example.nutmeg.api.room;

/** Room-level lifecycle. Each game has its own finer-grained "stage" on top. */
public enum Status {
    LOBBY,
    PLAYING,
    FINISHED
}
