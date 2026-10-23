package com.citics.glxt.contractchange.embedding;

import com.citics.glxt.contractchange.common.constants.CommonConstants;
import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;

/** Embedding 服务明确返回额度耗尽时使用的可识别异常。 */
public class EmbeddingQuotaExceededException extends ContractChangeBusinessException {
    private static final long serialVersionUID = 1L;

    public EmbeddingQuotaExceededException(String message, Throwable cause) {
        super(CommonConstants.SERVICE_UNAVAILABLE, message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
