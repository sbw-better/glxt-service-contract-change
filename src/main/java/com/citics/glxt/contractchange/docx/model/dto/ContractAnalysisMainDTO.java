package com.citics.glxt.contractchange.docx.model.dto;

import com.citics.glxt.contractchange.docx.domain.ContractAnalysisMain;
import io.swagger.annotations.ApiModel;
import lombok.Data;

@Data
@ApiModel(value = "合同解析主表dto", description = "")
public class ContractAnalysisMainDTO extends ContractAnalysisMain {

    private static final long serialVersionUID = 4003788644527138324L;
}
