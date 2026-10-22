package com.citics.glxt.contractchange.contractquery.mapper;

import com.citics.glxt.contractchange.contractquery.domain.TpifCpwdk;
import com.citics.glxt.contractchange.contractquery.model.ContractInfo;
import org.apache.ibatis.annotations.Param;

import java.sql.Date;
import java.util.List;

public interface TpifCpwdkMapper {
    int deleteByPrimaryKey(Long id);

    int insert(TpifCpwdk record);

    int insertSelective(TpifCpwdk record);

    TpifCpwdk selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(TpifCpwdk record);

    int updateByPrimaryKeyWithBLOBs(TpifCpwdk record);

    int updateByPrimaryKey(TpifCpwdk record);

    /** 查询管理人指定日期范围内有效的合同文档。 */
    List<ContractInfo> selectContracts(@Param("managerId") Long managerId,
                                       @Param("startDate") Date startDate,
                                       @Param("endDate") Date endDate);

    /** 批量返回有效、属于管理人且分类为合同/托管协议的可下载记录。 */
    List<TpifCpwdk> selectOwnedContractsForDownload(@Param("fileIds") List<Long> fileIds,
                                                    @Param("managerId") Long managerId);
}
