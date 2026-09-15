package com.citics.glxt.contractchange.contractcompare.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 按分析类型校验合同分析请求所需字段。 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ContractCompareRequestValidator.class)
public @interface ValidContractCompareRequest {
    String message() default "合同分析请求参数错误";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
