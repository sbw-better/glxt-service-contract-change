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

import javax.validation.Valid;

/** 合同修改前后版本条款比较接口。 */
@Validated
@RestController
@RequestMapping("/service/contract-compare")
@Api(tags = "合同双版本条款比较")
public class ContractCompareController {
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
}
