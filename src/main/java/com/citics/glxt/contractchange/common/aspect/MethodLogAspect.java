package com.citics.glxt.contractchange.common.aspect;

import cn.hutool.json.JSONUtil;
import com.citics.glxt.contractchange.common.annotation.MethodLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 切面,打印方法的出入参日志
 *
 **/
@Order()
@Aspect
@Component
public class MethodLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLogAspect.class);

    @Pointcut("@annotation(com.citics.glxt.contractchange.common.annotation.MethodLog)")
    public void methodLogAspect() {
        //
    }

    /**
     * 环绕通知
     *
     * @return Object
     */
    @Around("methodLogAspect()")
    public Object aroundMethod(ProceedingJoinPoint point) throws Throwable {
        String methodName = point.getSignature().getName();

        /*
         * 注解信息 获取方法编号、方法中文描述
         */
        String logDesc = "";
        try {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            MethodLog log = method.getAnnotation(MethodLog.class);
            logDesc = log.desc();
        } catch (Exception e) {
            LOGGER.error("获取注解信息异常！", e);
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("----------METHOD：【{}-{}】----- INPUT：【{}】",
                    logDesc, methodName, JSONUtil.toJsonStr(point.getArgs()));
        }

        Object result = point.proceed();

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("----------METHOD：【{}-{}】----- OUTPUT：【{}】",
                    logDesc, methodName, JSONUtil.toJsonStr(result));
        }

        return result;
    }
}
