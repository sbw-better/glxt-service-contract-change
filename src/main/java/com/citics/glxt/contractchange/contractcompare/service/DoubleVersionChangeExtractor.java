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

    /** 双版本策略：读取前后合同、应用Word修订、解析结构，再计算条款和段落差异。 */
    @Override
    public ContractCompareResponse extract(ContractCompareRequest request) {
        // SFTP读取和Aspose修订比较先生成“修订后的前后文档”及修订定位信号。
        byte[] oldBytes = fileLoader.load(request.getOldFileGetPath());
        byte[] newBytes = fileLoader.load(request.getNewFileGetPath());
        ComparisonResult compared = asposeCompareService.compare(oldBytes, newBytes);
        try {
            // 解析器负责恢复合同层级；比较引擎负责匹配前后条款并生成变化段落。
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
