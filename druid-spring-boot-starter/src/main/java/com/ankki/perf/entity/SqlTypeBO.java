package com.ankki.perf.entity;

import lombok.Data;

/**
 * @author fay
 * @date 2026-06-04
 * @description
 */
@Data
public class SqlTypeBO {
    private Long id;

    private Integer dbType;

    private String operType;

    private String operSentence;
}
