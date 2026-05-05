package com.fffg.cex.common.result;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "success";
    private static final int FAIL_CODE = 500;

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T>  success(T data){
        return new ApiResponse<>(SUCCESS_CODE,SUCCESS_MESSAGE, data);
    }
    public static ApiResponse<Void> success(){
        return new ApiResponse<>(SUCCESS_CODE,SUCCESS_MESSAGE, null);
    }
    public static <T> ApiResponse<T> fail(int code,String message){
        return new ApiResponse<>(code ,message, null);
    }
    public static  ApiResponse<Void> fail(String message){
        return new ApiResponse<>(FAIL_CODE,message, null);
    }
}
