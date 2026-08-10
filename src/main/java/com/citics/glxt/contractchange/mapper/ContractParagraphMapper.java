package com.citics.glxt.contractchange.mapper;

import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 历史合同段落数据访问接口。
 *
 * <p>所有业务 SQL 均定义在同名 Mapper XML 中，本接口不使用 SQL 注解。</p>
 */
public interface ContractParagraphMapper {
    /** 插入一条历史样本，主键在 XML 中通过 Sequence 回填。 */
    int insertParagraph(ContractParagraphDO paragraph);

    /** 查询启用状态下指定文本 Hash 的历史样本。 */
    ContractParagraphDO selectByTextHash(@Param("textHash") String textHash);

    /** 加载指定模型版本和维度下的全部有效样本。 */
    List<ContractParagraphDO> selectActiveParagraphs(@Param("modelVersion") String modelVersion,
                                                     @Param("vectorDim") Integer vectorDim);

}
