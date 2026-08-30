package com.citics.glxt.contractchange.docx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisMain;

public interface ContractAnalysisMainMapper extends BaseMapper<ContractAnalysisMain> {
    int insert(ContractAnalysisMain obj);
}
