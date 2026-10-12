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

    /** 只在指定版本中查重，包括该版本停用记录。 */
    List<ContractParagraphDO> selectByTextHashes(@Param("textHashes") List<String> textHashes,
                                               @Param("modelVersion") String modelVersion);

    List<ContractParagraphDO> selectAllActiveParagraphs();
    int countActiveParagraphs(@Param("modelVersion") String modelVersion);
    int disableActiveParagraphs(@Param("modelVersion") String modelVersion);

    /** 更新现有Hash记录的正文、标签、向量、模型版本并重新启用。 */
    int updateParagraph(ContractParagraphDO paragraph);

    /** 判断是否为空库；失效历史不计入有效样本容量。 */
    int countAllParagraphs();

    /** 加载指定模型版本和维度下的全部有效样本。 */
    List<ContractParagraphDO> selectActiveParagraphs(@Param("modelVersion") String modelVersion,
                                                     @Param("vectorDim") Integer vectorDim);

}
