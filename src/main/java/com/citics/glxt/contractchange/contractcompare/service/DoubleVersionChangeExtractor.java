package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService.ComparisonResult;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.service.ClauseComparisonEngine.Analysis;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 双版本合同策略：生成完整、未裁剪的条款比较结果。 */
@Service
public class DoubleVersionChangeExtractor implements ContractChangeExtractor {
    private final SftpContractFileLoader fileLoader;
    private final AsposeCompareService asposeCompareService;
    private final ContractStructureParser structureParser;
    private final ClauseComparisonEngine comparisonEngine;

    public DoubleVersionChangeExtractor(SftpContractFileLoader fileLoader,
                                        AsposeCompareService asposeCompareService,
                                        ContractStructureParser structureParser,
                                        ClauseComparisonEngine comparisonEngine) {
        this.fileLoader = fileLoader;
        this.asposeCompareService = asposeCompareService;
        this.structureParser = structureParser;
        this.comparisonEngine = comparisonEngine;
    }

    @Override
    public AnalysisType supportType() {
        return AnalysisType.DOUBLE_VERSION;
    }

    @Override
    public void validateRequest(ContractCompareRequest request) {
        if (!StringUtils.hasText(request.getOldFileGetPath())) {
            throw new ContractChangeBusinessException("修改前合同文件路径不能为空");
        }
        if (!StringUtils.hasText(request.getNewFileGetPath())) {
            throw new ContractChangeBusinessException("修改后合同文件路径不能为空");
        }
    }

    @Override
    public ContractCompareResponse extract(ContractCompareRequest request) {
        byte[] oldBytes = fileLoader.load(request.getOldFileGetPath());
        byte[] newBytes = fileLoader.load(request.getNewFileGetPath());
        ComparisonResult compared = asposeCompareService.compare(oldBytes, newBytes);
        try {
            Parsed oldContract = structureParser.parse(compared.getOldDocument());
            Parsed newContract = structureParser.parse(compared.getNewDocument());
            Analysis analysis = comparisonEngine.analyze(
                    oldContract, newContract, compared.getRevisions());
            return new ContractCompareResponse(analysis.getChanges().size(),
                    analysis.getChanges(), analysis.getWarnings());
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new ContractChangeBusinessException("合同结构解析失败");
        }
    }
}
