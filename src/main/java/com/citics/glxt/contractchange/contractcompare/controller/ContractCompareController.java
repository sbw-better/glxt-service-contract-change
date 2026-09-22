package com.citics.glxt.contractchange.contractcompare.controller;

import com.citics.glxt.contractchange.common.result.ContractChangeResult;
import com.citics.glxt.contractchange.contractcompare.model.ContractChangeAnalysisResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.service.ContractChangeAnalysisService;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 合同双版本比较和变更函条款提取接口。 */
@RestController
@RequestMapping("/service/contract-compare")
@Api(tags = "合同条款比较与提取")
public class ContractCompareController {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** /export 使用的详细比较与 Excel 导出链路。 */
    private final ContractCompareService compareService;

    /** /compare 使用的统一业务分析链路。 */
    private final ContractChangeAnalysisService analysisService;

    public ContractCompareController(ContractCompareService compareService,
                                     ContractChangeAnalysisService analysisService) {
        this.compareService = compareService;
        this.analysisService = analysisService;
    }

    @PostMapping("/compare")
    @ApiOperation(value = "分析合同变更业务类型",
            notes = "请求体必须包含userId、instId和analysisType；仅返回analysisId、fileChangeTypeCodes和analysisStatus")
    public ContractChangeResult<ContractChangeAnalysisResponse> compare(
            @RequestBody ContractCompareRequest request) {
        return ContractChangeResult.success(analysisService.analyze(request));
    }

    @PostMapping("/export")
    @ApiOperation(value = "导出合同分析结果Excel",
            notes = "仅导出双版本差异或单文件变更内容，不执行历史向量业务类型识别；根据analysisType选择导出内容")
    public void export(
                       @RequestBody ContractCompareRequest request,
                       HttpServletResponse response) throws IOException {
        byte[] excel = compareService.exportExcel(request);
        response.setContentType(XLSX_MEDIA_TYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=" + exportFilename(request));
        response.setContentLength(excel.length);
        response.getOutputStream().write(excel);
        response.flushBuffer();
    }

    private String exportFilename(ContractCompareRequest request) {
        return request.getAnalysisType() == AnalysisType.CHANGE_DOCUMENT
                ? "contract-change-extract-result.xlsx" : "contract-compare-result.xlsx";
    }
}
