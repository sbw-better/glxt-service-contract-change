package com.citics.glxt.contractchange.docx.aspose.controller;

import com.citics.glxt.contractchange.common.annotation.MethodLog;
import com.citics.glxt.contractchange.common.result.ResultModel;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeAnalyzeAndLandService;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeGenerateSupplementService;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeJbrKeywordsService;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeReplaceService;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/** 新增的 Aspose 路由；旧版 docx4j 路由保持不变。 */
@Api(value = "Aspose docx处理", tags = "Aspose docx处理")
@RestController
@RequestMapping("/service/basic/docx/aspose")
@Slf4j
public class AsposeDocxController {
    @Resource private AsposeGenerateSupplementService generateService;
    @Resource private AsposeAnalyzeAndLandService analyzeService;
    @Resource private AsposeReplaceService replaceService;
    @Resource private AsposeJbrKeywordsService jbrService;

    @ApiOperation("Aspose：根据修订文件生成补充协议")
    @MethodLog(desc = "Aspose：根据修订文件生成补充协议")
    @PostMapping("/generateSupplementByRevision")
    public ResultModel<?> generateSupplementByRevision(@RequestBody DocxAnalysisDTO request) {
        return stringResult("根据修订文件生成补充协议文件时出错，请检查。", () ->
                generateService.generate(request));
    }

    @ApiOperation("Aspose：解析文件并落地段落信息")
    @PostMapping("/analyzeAndLand")
    public ResultModel<?> analyzeAndLand(@RequestBody DocxAnalysisDTO request) {
        try {
            Long id = analyzeService.analyzeAndLand(request);
            return ResultModel.success("操作成功", id);
        } catch (Exception e) {
            log.error("Aspose 合同解析落地失败", e);
            return ResultModel.error("合同解析落地时出错。报错详情：" + safeMessage(e));
        }
    }

    @ApiOperation("Aspose：文档内容替换")
    @MethodLog(desc = "Aspose：文档内容替换")
    @PostMapping("/replace")
    public ResultModel<?> replace(@RequestBody DocxAnalysisDTO request) {
        return stringResult("文档内容替换时出错，请检查。", () -> replaceService.replace(request));
    }

    @ApiOperation("Aspose：经办人关键字处理")
    @MethodLog(desc = "Aspose：经办人关键字处理")
    @PostMapping("/jbrKeywordsHandle")
    public ResultModel<?> jbrKeywordsHandle(@RequestBody DocxAnalysisDTO request) {
        return stringResult("经办人关键字处理时出错，请检查。", () -> jbrService.handle(request));
    }

    private ResultModel<?> stringResult(String errorPrefix, ResultSupplier action) {
        try {
            String result = action.get();
            return StringUtils.isEmpty(result)
                    ? ResultModel.success("操作成功")
                    : ResultModel.success(result, "操作成功");
        } catch (Exception e) {
            log.error("Aspose Docx 接口处理失败", e);
            return ResultModel.error(errorPrefix + "报错详情：" + safeMessage(e));
        }
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private interface ResultSupplier { String get(); }
}

