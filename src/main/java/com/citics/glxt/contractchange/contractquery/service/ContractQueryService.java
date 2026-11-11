package com.citics.glxt.contractchange.contractquery.service;

import com.citics.glxt.contractchange.contractquery.model.ContractInfo;
import com.citics.glxt.contractchange.contractquery.model.ContractQueryRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadPayload;
import com.citics.glxt.contractchange.contractquery.model.ContractPageQueryRequest;
import com.citics.glxt.contractchange.common.page.PageInfoResponse;

import java.util.List;

/** 合同文档查询业务服务。 */
public interface ContractQueryService {
    List<ContractInfo> queryContracts(ContractQueryRequest request);

    PageInfoResponse<ContractInfo> queryContractsPage(ContractPageQueryRequest request);

    /** 批量校验管理人对文件的归属后下载原始文件。 */
    ContractDownloadPayload downloadContracts(ContractDownloadRequest request);
}
