package com.citics.glxt.contractchange.docx.model.dto;

import com.citics.glxt.contractchange.docx.domain.ContractAnalysisDetail;
import io.swagger.annotations.ApiModel;
import lombok.Data;

@Data
@ApiModel(value = "合同解析明细表dto", description = "")
public class ContractAnalysisDetailDTO extends ContractAnalysisDetail {

    private static final long serialVersionUID = -3409178230103540245L;
}
