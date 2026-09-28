package com.example.nutmeg.api.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.nutmeg.api.room.RoomException;

/** RFC 9457 problem details. The client switches on "code", the human reads "detail". */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RoomException.class)
    public ProblemDetail onRoomError(RoomException ex) {
        HttpStatus status = switch (ex.code()) {
            case "ROOM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ROOM_FULL", "GAME_IN_PROGRESS", "NAME_TAKEN" -> HttpStatus.CONFLICT;
            case "AT_CAPACITY", "CODE_EXHAUSTED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.code());
        return problem;
    }
}
