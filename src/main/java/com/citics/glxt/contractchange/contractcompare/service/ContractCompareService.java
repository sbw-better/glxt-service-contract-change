package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService.ComparisonResult;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.service.ClauseComparisonEngine.Analysis;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import org.springframework.stereotype.Service;

/** 合同双版本比较应用服务。 */
@Service
public class ContractCompareService {
    private final SftpContractFileLoader fileLoader;
    private final AsposeCompareService asposeCompareService;
    private final ContractStructureParser structureParser;
    private final ClauseComparisonEngine comparisonEngine;

    public ContractCompareService(SftpContractFileLoader fileLoader,
                                  AsposeCompareService asposeCompareService,
                                  ContractStructureParser structureParser,
                                  ClauseComparisonEngine comparisonEngine) {
        this.fileLoader = fileLoader;
        this.asposeCompareService = asposeCompareService;
        this.structureParser = structureParser;
        this.comparisonEngine = comparisonEngine;
    }

    public ContractCompareResponse compare(ContractCompareRequest request) {
        byte[] oldBytes = fileLoader.load(request.getOldFileGetPath());
        byte[] newBytes = fileLoader.load(request.getNewFileGetPath());
        ComparisonResult compared = asposeCompareService.compare(oldBytes, newBytes);
        try {
            Parsed oldContract = structureParser.parse(compared.getOldDocument(), "OLD");
            Parsed newContract = structureParser.parse(compared.getNewDocument(), "NEW");
            Analysis analysis = comparisonEngine.analyze(oldContract, newContract, compared.getRevisions());
            return new ContractCompareResponse(analysis.getChanges().size(),
                    analysis.getChanges(), analysis.getWarnings());
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new com.citics.glxt.common.exception.ContractChangeBusinessException("合同结构解析失败");
        }
    }
}
