package com.citics.glxt.contractchange.common.controller;

import com.citics.glxt.contractchange.common.annotation.MethodLog;
import com.citics.glxt.contractchange.common.result.ResultModel;
import com.citics.glxt.contractchange.common.service.CommonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static com.citics.glxt.contractchange.common.constants.CommonConstants.FTP_FILE_RETURN_TYPE_FILENAME;

@Slf4j
@Api(tags = {"通用接口"})
@Controller
@RequestMapping("/service/basic/common")
public class CommonController {
    @Autowired
    private CommonService commonService;

    /**
     * 从FTP文档库下载文件
     */
    @ApiOperation(value = "从FTP文档库下载文件")
    @MethodLog(desc = "从FTP文档库下载文件")
    @ResponseBody
    @GetMapping("/doc/query/{id}")
    public ResultModel<?> downloadFromDocLib(@PathVariable Long id)
    {
        String docName = commonService.downloadFromDocLib(id, FTP_FILE_RETURN_TYPE_FILENAME);
        if (StringUtils.isEmpty(docName)) {
            return ResultModel.error("下载失败");
        }
        return ResultModel.success(docName);
    }

}
