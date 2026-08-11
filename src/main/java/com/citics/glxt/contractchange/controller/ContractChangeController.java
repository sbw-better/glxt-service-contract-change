package com.citics.glxt.contractchange.controller;

import com.citics.glxt.contractchange.common.ContractChangeResult;
import com.citics.glxt.contractchange.model.ImportResponse;
import com.citics.glxt.contractchange.model.IndexStatusResponse;
import com.citics.glxt.contractchange.model.PredictRequest;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.service.ContractParagraphImportService;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService;
import com.citics.glxt.contractchange.service.ParagraphVectorIndexService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * 合同段落变更类型识别 REST 接口。
 *
 * <p>控制器只负责协议转换和参数校验，业务日志由下层服务统一记录，避免同一请求重复输出。</p>
 */
@Validated
@RestController
@RequestMapping("/service/contract-change")
@Api(tags = "合同段落变更类型识别")
public class ContractChangeController {
    private final ContractParagraphImportService importService;
    private final ContractParagraphPredictionService predictionService;
    private final ParagraphVectorIndexService indexService;

    /** 注入导入、预测和索引运维三个核心业务服务。 */
    public ContractChangeController(ContractParagraphImportService importService,
                                    ContractParagraphPredictionService predictionService,
                                    ParagraphVectorIndexService indexService) {
        this.importService = importService;
        this.predictionService = predictionService;
        this.indexService = indexService;
    }

    /** 导入历史段落与变更类型编码对应关系。 */
    @PostMapping(value = "/samples/import", consumes = "multipart/form-data")
    @ApiOperation("导入历史合同段落Excel")
    public ContractChangeResult<ImportResponse> importSamples(@RequestPart("file") MultipartFile file) {
        return ContractChangeResult.success(importService.importExcel(file));
    }

    /** 识别一个新合同段落可能对应的多个变更类型。 */
    @PostMapping("/predict")
    @ApiOperation("预测新合同段落的变更类型")
    public ContractChangeResult<PredictionResponse> predict(@Valid @RequestBody PredictRequest request) {
        return ContractChangeResult.success(predictionService.predict(request.getParagraph()));
    }

    /** 原子重建 JVM 内存向量索引，不重新生成向量。 */
    @PostMapping("/index/reload")
    @ApiOperation("从Oracle重新加载历史段落向量索引")
    public ContractChangeResult<IndexStatusResponse> reloadIndex() {
        return ContractChangeResult.success(indexService.reload());
    }

    /** 查询当前实际提供检索服务的索引快照状态。 */
    @GetMapping("/index/status")
    @ApiOperation("查询内存向量索引状态")
    public ContractChangeResult<IndexStatusResponse> indexStatus() {
        return ContractChangeResult.success(indexService.status());
    }
}
