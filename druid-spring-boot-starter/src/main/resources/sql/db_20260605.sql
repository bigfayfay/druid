-- DROP TABLE IF EXISTS bs_audit.sql_template_res;
CREATE TABLE bs_audit.sql_template_res  (
    `id`       bigint(20) unsigned NOT NULL COMMENT '主键',
    `status`   varchar(12) NOT NULL ,
    `cost_ns`  bigint,
    `db_type`  int ,
    `fail_num`  int default 0,
    `sql_len`  int,
    `sql_md5`   char(32)  NOT NULL,
    `oper_type` varchar(20)  ,
    `oper_sentence` text ,
    `fail_reason` text ,
    `remark` varchar(255),
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    UNIQUE KEY `idx_sql_md5` (`sql_md5`)
) ENGINE=InnoDB AUTO_INCREMENT=10001 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC ;