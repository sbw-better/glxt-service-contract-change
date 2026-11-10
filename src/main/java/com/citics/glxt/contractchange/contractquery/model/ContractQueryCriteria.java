package com.citics.glxt.contractchange.contractquery.model;

import lombok.Data;

import java.sql.Date;

/** Mapper 使用的合同文档筛选条件。 */
@Data
public class ContractQueryCriteria {
    private Long managerId;
    private Date startDate;
    private Date endDate;
}
