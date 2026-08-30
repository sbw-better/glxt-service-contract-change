package com.citics.glxt.contractchange.docx.controller;

import com.citics.glxt.common.result.ResultModel;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.DocxModifyAndLandService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static com.citics.glxt.common.constants.CommonConstants.EXECUTE_EXECUTE_FAIL;
import static com.citics.glxt.common.constants.CommonConstants.EXECUTE_EXECUTE_SUCCESS;

@Api(value = "docx文件解析", tags = "docx文件解析")
@Validated
@RestController
@RequestMapping("/service/basic/docx")
@Slf4j
public class DocxController {

    @Resource
    private DocxModifyAndLandService docxModifyAndLandService;

    @ApiOperation(value = "解析文件落地段落信息", notes = "解析文件落地段落信息")
    @ResponseBody
    @PostMapping("/analyzeAndLand")
    public ResultModel<?> analyzeAndLand(@RequestBody DocxAnalysisDTO docxAnalysisDTO) {
        Integer res = EXECUTE_EXECUTE_SUCCESS;
        String msg = "";
        Long id = -99L;
        try {
            // 返回主对象ID，即TPIF_HTJXZB的ID
            id = this.docxModifyAndLandService.analyzeAndLand(docxAnalysisDTO);
        } catch (Exception e) {
            e.printStackTrace();
            msg = e.getMessage();
            res = EXECUTE_EXECUTE_FAIL;
        }
        if (res.equals(EXECUTE_EXECUTE_SUCCESS)) {
            return ResultModel.success("操作成功", id);
        } else {
            return ResultModel.error("合同解析落地时出错。报错详情：" + msg);
        }
    }

}
