package com.citics.glxt.contractchange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citics.glxt.contractchange.domain.ContractAnalysisMain;

public interface ContractAnalysisMainMapper extends BaseMapper<ContractAnalysisMain> {
    int insert(ContractAnalysisMain obj);
}
