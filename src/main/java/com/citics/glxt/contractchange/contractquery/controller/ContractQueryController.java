package com.citics.glxt.contractchange.contractquery.controller;

import com.citics.glxt.contractchange.common.result.ContractChangeResult;
import com.citics.glxt.contractchange.contractquery.model.ContractQueryRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractInfo;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadPayload;
import com.citics.glxt.contractchange.contractquery.service.ContractQueryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import javax.validation.Valid;
import java.util.List;
import java.io.IOException;

/** 查询管理人名下指定日期范围内有效的合同文档元数据。 */
@RestController
@RequestMapping("/service/contract-query")
@Api(tags = "合同文档查询")
public class ContractQueryController {
    private final ContractQueryService queryService;

    public ContractQueryController(ContractQueryService queryService) {
        this.queryService = queryService;
    }

    /** 参数通过 application/json 请求体传入；queryDays 缺省时按0天查询。 */
    @PostMapping(value = "/doc/query", consumes = "application/json")
    @ApiOperation("按管理人和日期范围查询有效合同文档")
    public ContractChangeResult<List<ContractInfo>> queryContracts(
            @Valid @RequestBody ContractQueryRequest request) {
        return ContractChangeResult.success(queryService.queryContracts(request));
    }

    /** 校验管理人和文件归属后，以附件流方式返回文档，供客户端下载。 */
    @PostMapping(value = "/doc/download", consumes = "application/json")
    @ApiOperation("校验管理人归属并下载合同文档，多个文件时返回ZIP")
    public void downloadContracts(@Valid @RequestBody ContractDownloadRequest request,
                                  HttpServletResponse response) throws IOException {
        ContractDownloadPayload download = queryService.downloadContracts(request);
        if (download == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(download.getContentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.getFallbackFileName()
                + "\"; filename*=UTF-8''"
                + org.springframework.web.util.UriUtils.encode(download.getFileName(), "UTF-8"));
        response.setContentLength(download.getContent().length);
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            outputStream.write(download.getContent());
            outputStream.flush();
        }
    }
}
