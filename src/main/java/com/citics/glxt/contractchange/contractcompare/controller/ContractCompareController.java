package com.citics.glxt.contractchange.contractcompare.controller;

import com.citics.glxt.common.result.ContractChangeResult;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
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

/** 合同修改前后版本条款比较接口。 */
@Validated
@RestController
@RequestMapping("/service/contract-compare")
@Api(tags = "合同双版本条款比较")
public class ContractCompareController {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ContractCompareService compareService;

    public ContractCompareController(ContractCompareService compareService) {
        this.compareService = compareService;
    }

    @PostMapping("/compare")
    @ApiOperation(value = "比较修改前和修改后的合同",
            notes = "从服务器路径读取两份DOCX，返回按合同结构聚合的新增、删除和修改条款")
    public ContractChangeResult<ContractCompareResponse> compare(
            @Valid @RequestBody ContractCompareRequest request) {
        return ContractChangeResult.success(compareService.compare(request));
    }

    @PostMapping("/export")
    @ApiOperation(value = "导出修改前后合同的比对结果Excel",
            notes = "请求参数与compare接口相同，返回一个变化段落一行的xlsx文件")
    public void export(@Valid @RequestBody ContractCompareRequest request,
                       HttpServletResponse response) throws IOException {
        byte[] excel = compareService.exportExcel(request);
        response.setContentType(XLSX_MEDIA_TYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=contract-compare-result.xlsx");
        response.setContentLength(excel.length);
        response.getOutputStream().write(excel);
        response.flushBuffer();
    }
}
