package com.citics.glxt.contractchange.contractcompare.validation;

import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/** 对双版本和单文件模式执行互不干扰的条件必填校验。 */
public class ContractCompareRequestValidator
        implements ConstraintValidator<ValidContractCompareRequest, ContractCompareRequest> {
    @Override
    public boolean isValid(ContractCompareRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        AnalysisType type = request.getAnalysisType() == null
                ? AnalysisType.DOUBLE_VERSION : request.getAnalysisType();
        if (type == AnalysisType.CHANGE_DOCUMENT) {
            if (!StringUtils.hasText(request.getChangeFileGetPath())) {
                return violation(context, "变更函文件路径不能为空", "changeFileGetPath");
            }
            return true;
        }
        if (!StringUtils.hasText(request.getOldFileGetPath())) {
            return violation(context, "修改前合同文件路径不能为空", "oldFileGetPath");
        }
        if (!StringUtils.hasText(request.getNewFileGetPath())) {
            return violation(context, "修改后合同文件路径不能为空", "newFileGetPath");
        }
        return true;
    }

    private boolean violation(ConstraintValidatorContext context, String message,
                              String property) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property).addConstraintViolation();
        return false;
    }
}
