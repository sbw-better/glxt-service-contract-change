package com.citics.glxt.contractchange.docx.controller;

import com.citics.glxt.contractchange.common.annotation.MethodLog;
import com.citics.glxt.contractchange.common.result.ResultModel;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static com.citics.glxt.contractchange.common.constants.CommonConstants.EXECUTE_EXECUTE_FAIL;
import static com.citics.glxt.contractchange.common.constants.CommonConstants.EXECUTE_EXECUTE_SUCCESS;

@Api(value = "docx文件解析", tags = "docx文件解析")
@Validated
@RestController
@RequestMapping("/service/basic/docx")
@Slf4j
public class DocxController {

    @Resource
    DocxModifyByConfigService docxModifyByConfigService;

    @Resource
    DocxModifyAndLandService docxModifyAndLandService;

    @Resource
    DocxGenerateByRevision docxGenerateByRevision;

    @Resource
    DocxReplaceContent docxReplaceContent;

    @Resource
    DocxHandleToolService docxHandleToolService;

    @ApiOperation(
            value = "解析并根据配置（协商函）生成新版本合同",
            notes = "解析并根据配置（协商函）生成新版本合同"
    )
    @MethodLog(desc = "解析并根据配置（协商函）生成新版本合同")
    @ResponseBody
    @PostMapping("/analyzeAndGenerate")
    public ResultModel<?> analyzeAndGenerate(@RequestBody DocxAnalysisDTO docxAnalysisDTO) {
        Integer res = EXECUTE_EXECUTE_SUCCESS;
        String resStr = "";
        String msg = "";

        try {
            resStr = this.docxModifyByConfigService.analyzeAndGenerate(docxAnalysisDTO);
        } catch (Exception e) {
            e.printStackTrace();
            msg = e.getMessage();
            res = EXECUTE_EXECUTE_FAIL;
        }

        if (res.equals(EXECUTE_EXECUTE_SUCCESS)) {
            if (StringUtils.isEmpty(resStr)) {
                return ResultModel.success("操作成功");
            } else {
                return ResultModel.success(resStr, "操作成功");
            }
        } else {
            return ResultModel.error("合同生成时出错，请检查合同生成情况。报错详情：" + msg);
        }
    }

    @ApiOperation(
            value = "根据修订文件生成补充协议文件",
            notes = "根据修订文件生成补充协议文件"
    )
    @MethodLog(desc = "根据修订文件生成补充协议文件")
    @ResponseBody
    @PostMapping("/generateSupplementByRevision")
    public ResultModel<?> generateSupplementByRevision(
            @RequestBody DocxAnalysisDTO docxAnalysisDTO) {

        Integer res = EXECUTE_EXECUTE_SUCCESS;
        String resStr = "";
        String msg = "";

        try {
            resStr = this.docxGenerateByRevision.generateByRevision(docxAnalysisDTO);
        } catch (Exception e) {
            e.printStackTrace();
            msg = e.getMessage();
            res = EXECUTE_EXECUTE_FAIL;
        }

        if (res.equals(EXECUTE_EXECUTE_SUCCESS)) {
            if (StringUtils.isEmpty(resStr)) {
                return ResultModel.success("操作成功");
            } else {
                return ResultModel.success(resStr, "操作成功");
            }
        } else {
            return ResultModel.error(
                    "根据修订文件生成补充协议文件时出错，请检查。报错详情：" + msg
            );
        }
    }

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

    @ApiOperation(
            value = "文档内容替换",
            notes = "文档内容替换"
    )
    @MethodLog(desc = "文档内容替换")
    @ResponseBody
    @PostMapping("/replace")
    public ResultModel<?> replaceContent(@RequestBody DocxAnalysisDTO docxAnalysisDTO) {
        Integer res = EXECUTE_EXECUTE_SUCCESS;
        String resStr = "";
        String msg = "";

        try {
            resStr = this.docxReplaceContent.replaceContentMain(docxAnalysisDTO);
        } catch (Exception e) {
            e.printStackTrace();
            msg = e.getMessage();
            res = EXECUTE_EXECUTE_FAIL;
        }

        if (res.equals(EXECUTE_EXECUTE_SUCCESS)) {
            if (StringUtils.isEmpty(resStr)) {
                return ResultModel.success("操作成功");
            } else {
                return ResultModel.success(resStr, "操作成功");
            }
        } else {
            return ResultModel.error(
                    "文档内容替换时出错，请检查。报错详情：" + msg
            );
        }
    }

    @ApiOperation(
            value = "经办人关键字处理",
            notes = "经办人关键字处理"
    )
    @MethodLog(desc = "经办人关键字处理")
    @ResponseBody
    @PostMapping("/jbrKeywordsHandle")
    public ResultModel<?> jbrKeywordsHandle(@RequestBody DocxAnalysisDTO docxAnalysisDTO) {
        Integer res = EXECUTE_EXECUTE_SUCCESS;
        String resStr = "";
        String msg = "";

        try {
            resStr = this.docxHandleToolService.jbrKeywordsHandle(docxAnalysisDTO);
        } catch (Exception e) {
            e.printStackTrace();
            msg = e.getMessage();
            res = EXECUTE_EXECUTE_FAIL;
        }

        if (res.equals(EXECUTE_EXECUTE_SUCCESS)) {
            if (StringUtils.isEmpty(resStr)) {
                return ResultModel.success("操作成功");
            } else {
                return ResultModel.success(resStr, "操作成功");
            }
        } else {
            return ResultModel.error(
                    "经办人关键字处理时出错，请检查。报错详情：" + msg
            );
        }
    }
}
