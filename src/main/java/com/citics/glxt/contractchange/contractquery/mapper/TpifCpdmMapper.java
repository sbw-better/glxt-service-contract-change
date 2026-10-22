package com.citics.glxt.contractchange.contractquery.mapper;

import com.citics.glxt.contractchange.contractquery.domain.TpifCpdm;

public interface TpifCpdmMapper {
    int deleteByPrimaryKey(Long id);

    int insert(TpifCpdm record);

    int insertSelective(TpifCpdm record);

    TpifCpdm selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(TpifCpdm record);

    int updateByPrimaryKeyWithBLOBs(TpifCpdm record);

    int updateByPrimaryKey(TpifCpdm record);
}