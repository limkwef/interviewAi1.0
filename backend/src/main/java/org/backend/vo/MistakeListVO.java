package org.backend.vo;

import lombok.Data;

import java.util.List;

import org.backend.entity.MistakeRecord;

/**
 * 错题列表 VO
 */
@Data
public class MistakeListVO {
    private List<MistakeRecord> records;
    private Integer total;
    private Integer page;
    private Integer size;
}
