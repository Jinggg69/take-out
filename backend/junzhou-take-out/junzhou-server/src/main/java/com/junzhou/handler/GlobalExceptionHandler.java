package com.junzhou.handler;

import com.junzhou.constant.MessageConstant;
import com.junzhou.exception.BaseException;
import com.junzhou.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.DuplicateFormatFlagsException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(com.junzhou.exception.MiniappApiException.class)
    public org.springframework.http.ResponseEntity<Result<String>> miniapp(com.junzhou.exception.MiniappApiException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatus()).body(Result.error(ex.getMessage()));
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> invalidRequest(Exception ex) { return Result.error("请求参数错误"); }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<String> dependencyFailure(org.springframework.dao.DataAccessException ex) {
        log.error("业务数据访问失败",ex);
        return Result.error("服务暂不可用，请稍后重试");
    }

    /** 拒绝无权访问，不暴露内部鉴权细节。 */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<String> accessDenied(SecurityException ex) {
        return Result.error("无权访问");
    }

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理SQL异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        log.error("SQL约束异常", ex);
        //Duplicate entry 'zhangsan' for key 'employee.idx_username'
        String message = ex.getMessage();
        if (message.contains("Duplicate entry")) {
            String[] split = message.split(" ");
            String username = split[2];
            return Result.error(username + MessageConstant.ALREADY_EXIST);
        }else {
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }
}
