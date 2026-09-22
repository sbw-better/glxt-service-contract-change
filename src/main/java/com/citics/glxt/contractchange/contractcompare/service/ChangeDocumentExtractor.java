package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 单份变更类文件策略：复用现有变更函提取服务并保留完整结果。 */
@Service
public class ChangeDocumentExtractor implements ContractChangeExtractor {
    private final SftpContractFileLoader fileLoader;
    private final ChangeDocumentExtractionService extractionService;

    public ChangeDocumentExtractor(SftpContractFileLoader fileLoader,
                                   ChangeDocumentExtractionService extractionService) {
        this.fileLoader = fileLoader;
        this.extractionService = extractionService;
    }

    @Override
    public AnalysisType supportType() {
        return AnalysisType.CHANGE_DOCUMENT;
    }

    @Override
    public void validateRequest(ContractCompareRequest request) {
        if (!StringUtils.hasText(request.getChangeFileGetPath())) {
            throw new ContractChangeBusinessException("变更函文件路径不能为空");
        }
    }

    @Override
    public ContractCompareResponse extract(ContractCompareRequest request) {
        return extractionService.extract(fileLoader.load(request.getChangeFileGetPath()));
    }
}
