package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.model.DocumentChangeTypeSummaryResponse;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.util.ContractTextNormalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 预测文件级变更类型；预测成功后才调用已上线服务完成原解析落库。 */
@Service
public class ContractDocumentChangePredictionService {
    private final ContractDocumentParagraphReader paragraphReader;
    private final ContractParagraphPredictionService paragraphPredictionService;
    private final FileChangeTypeAggregator changeTypeAggregator;
    private final LegacyDocxAnalysisTransactionService legacyLandingService;

    public ContractDocumentChangePredictionService(ContractDocumentParagraphReader paragraphReader,
                                                   ContractParagraphPredictionService paragraphPredictionService,
                                                   FileChangeTypeAggregator changeTypeAggregator,
                                                   LegacyDocxAnalysisTransactionService legacyLandingService) {
        this.paragraphReader = paragraphReader;
        this.paragraphPredictionService = paragraphPredictionService;
        this.changeTypeAggregator = changeTypeAggregator;
        this.legacyLandingService = legacyLandingService;
    }

    public DocumentChangeTypeSummaryResponse analyzeAndPredict(DocxAnalysisDTO request, String userId) {
        List<String> predictionParagraphs = uniqueNormalizedParagraphs(
                paragraphReader.readPredictionParagraphs(request));
        List<PredictionResponse> predictions = paragraphPredictionService.predictBatch(predictionParagraphs, userId);
        List<String> codes = changeTypeAggregator.aggregate(predictionParagraphs, predictions);

        // 只在预测已经全部成功后开启数据库事务，避免模型调用占用数据库资源。
        Long mainId = legacyLandingService.analyzeAndLand(request);
        return new DocumentChangeTypeSummaryResponse(mainId, codes);
    }

    private List<String> uniqueNormalizedParagraphs(List<String> paragraphs) {
        Set<String> unique = new LinkedHashSet<String>();
        for (String paragraph : paragraphs) {
            String normalized = ContractTextNormalizer.normalize(paragraph);
            if (!normalized.isEmpty()) {
                unique.add(normalized);
            }
        }
        return new ArrayList<String>(unique);
    }
}
