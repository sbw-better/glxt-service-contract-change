package com.citics.glxt.contractchange.contractcompare.controller;

import com.citics.glxt.common.result.ContractChangeResult;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

/** 合同双版本比较和变更函条款提取接口。 */
@Validated
@RestController
@RequestMapping("/service/contract-compare")
@Api(tags = "合同条款比较与提取")
public class ContractCompareController {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ContractCompareService compareService;

    public ContractCompareController(ContractCompareService compareService) {
        this.compareService = compareService;
    }

    @PostMapping("/compare")
    @ApiOperation(value = "比较双版本合同或提取变更函条款",
            notes = "analysisType默认DOUBLE_VERSION；CHANGE_DOCUMENT按函件类型从单份DOCX提取变更条款")
    public ContractChangeResult<ContractCompareResponse> compare(
            @Valid @RequestBody ContractCompareRequest request) {
        return ContractChangeResult.success(compareService.compare(request));
    }

    @PostMapping("/export")
    @ApiOperation(value = "导出合同分析结果Excel",
            notes = "请求参数与compare接口相同；根据analysisType导出双版本比对或变更函提取结果")
    public void export(@Valid @RequestBody ContractCompareRequest request,
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
