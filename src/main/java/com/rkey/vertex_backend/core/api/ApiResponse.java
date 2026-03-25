package com.rkey.vertex_backend.core.api;

import java.time.OffsetDateTime;

public record ApiResponse<T> (
    String name,
    String message,
    T data,
    String responseCode,
    OffsetDateTime timeStamp
){
    

    public ApiResponse{
        if(timeStamp == null)
            timeStamp = OffsetDateTime.now(); // The time of the repsonse creation
    }
}
