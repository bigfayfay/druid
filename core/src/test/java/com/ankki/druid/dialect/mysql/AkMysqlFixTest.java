package com.ankki.druid.dialect.mysql;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.template.AkOutputVisitorUtils;
import com.ankki.druid.util.MyStringUtils;
import junit.framework.TestCase;


public class AkMysqlFixTest extends TestCase {
    public DbType dbtype = DbType.mysql;

    public int dbId = AkDbTypeEnum.of(dbtype).getTypeId();


    /**
     * 根因分析
     *
     *   解析失败是因为表别名 `cross` 与 SQL 关键字 CROSS 冲突，Druid SQL 解析器 SQLSelectParser.parseTableSourceRest() 在两个地方误处理：
     *
     *   ┌──────────────┬────────────────────────────────┬───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
     *   │     阶段     │              行号              │                                                               问题                                                                │
     *   ├──────────────┼────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
     *   │ 别名处理     │ SQLSelectParser.java:1577-1579 │ identifier "cross" 的 FNV hash 匹配 FnvHash.Constants.CROSS，代码跳过别名设置（以为它是 CROSS JOIN）                              │
     *   ├──────────────┼────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
     *   │ Join类型判断 │ SQLSelectParser.java:1758-1766 │ 解析器把 `cross` 当作 CROSS 关键字消费掉 (lexer.nextToken())，然后期望 JOIN，但实际是 INNER，不匹配任何分支，joinType 保持 null   │
     *   ├──────────────┼────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
     *   │ 返回上层     │ —                              │ INNER token 未被消费，上层解析器遇到它无法处理，抛出 ParserException("not supported. pos 1220, line 1, column 1216, token INNER") │
     *   └──────────────┴────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
     *
     *   修复内容
     *
     *   文件: core/src/test/sql/2/zdzsj_node116
     *   ├──────────────┼────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
     *   │ Join类型判断 │ SQLSelectParser.java:1758-1766 │ 解析器把 `cross` 当作 CROSS 关键字消费掉 (lexer.nextToken())，然后期望 JOIN，但实际是 INNER，不匹配任何分支，joinType 保持 null   │
     *   ├──────────────┼────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
     *   │ 返回上层     │ —                              │ INNER token 未被消费，上层解析器遇到它无法处理，抛出 ParserException("not supported. pos 1220, line 1, column 1216, token INNER") │
     *   └──────────────┴────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
     */
    public void test_fix_cross_(){
        // SQLSelectParser.  1577行
        String sql = "select            ro.rescue_id,            call_serial_num,            center_name,            call_name,            call_phone,            contact_phone,            ro.sex,            symptom,            ro.pt_oral_symptom oral_symptom,            special_case,            call_type.call_type_name,            rescue_address,            if( rocr.first_assign_time is null,-1,1) as dispatch_status,            if( rocr.first_assign_time is null,0,1) as ambulance_count,            ambulance.license_plate am_license_plate,            driver_name,            driver_telephone driver_phone,            doc_name doctor_name,            doctor.account doctor_phone,            stretcher.`name` assist_staff_name,            stretcher.telephone assist_staff_phone,            dispatcher.dispatcher_name,            rocr.first_assign_time assign_time,            ambulance_go_time,            ambulance_reach_time,            send_to_hos_time,            arrival_hospital_time arrival_time,            cancel.create_time cancel_time,            '' primary_diagnosis,            '' emergency_measures,            '' ecg_image        from cross_regional_rescue_bind cr                 inner join rescue_order_v2 ro on ro.rescue_id=cr.rescue_id                 inner join rescue_order_current_record rocr on ro.rescue_id=rocr.rescue_id                 inner join emergency_center center on center.center_id=ro.center_id                 inner join call_type on call_type.call_type_id=ro.call_type and call_type.rescue_type=1                 left join doctor on doctor.doc_id=ro.doc_id                 left join driver on driver.driver_id=ro.driver_id                 left join dispatcher on dispatcher.dispatcher_id=ro.dp_id                 left join ambulance on ambulance.ambulance_id=ro.ambulance_id                 left join stretcher on stretcher.stretcher_id=ro.stretcher_id                 left join rescue_status_record cancel on cancel.rescue_id=ro.rescue_id and cancel.rescue_status=-1        where cr.parent_rescue_center_id=70 and cr.create_time BETWEEN '2026-05-31 00:00:00' and '2026-05-31 23:59:59' and rocr.tag_type is null";
        sql = "select\u0014            ro.rescue_id,\u0014            call_serial_num,\u0014            center_name,\u0014            call_name,\u0014            call_phone,\u0014            contact_phone,\u0014            ro.sex,\u0014            symptom,\u0014            ro.pt_oral_symptom oral_symptom,\u0014            special_case,\u0014            call_type.call_type_name,\u0014            rescue_address,\u0014            if( rocr.first_assign_time is null,-1,1) as dispatch_status,\u0014            if( rocr.first_assign_time is null,0,1) as ambulance_count,\u0014            ambulance.license_plate am_license_plate,\u0014            driver_name,\u0014            driver_telephone driver_phone,\u0014            doc_name doctor_name,\u0014            doctor.account doctor_phone,\u0014            stretcher.`name` assist_staff_name,\u0014            stretcher.telephone assist_staff_phone,\u0014            dispatcher.dispatcher_name,\u0014            rocr.first_assign_time assign_time,\u0014            ambulance_go_time,\u0014            ambulance_reach_time,\u0014            send_to_hos_time,\u0014            arrival_hospital_time arrival_time,\u0014            cancel.create_time cancel_time,\u0014            '' primary_diagnosis,\u0014            '' emergency_measures,\u0014            '' ecg_image\u0014        from cross_regional_rescue_bind `cross`\u0014                 inner join rescue_order_v2 ro on ro.rescue_id=`cross`.rescue_id\u0014                 inner join rescue_order_current_record rocr on ro.rescue_id=rocr.rescue_id\u0014                 inner join emergency_center center on center.center_id=ro.center_id\u0014                 inner join call_type on call_type.call_type_id=ro.call_type and call_type.rescue_type=1\u0014                 left join doctor on doctor.doc_id=ro.doc_id\u0014                 left join driver on driver.driver_id=ro.driver_id\u0014                 left join dispatcher on dispatcher.dispatcher_id=ro.dp_id\u0014                 left join ambulance on ambulance.ambulance_id=ro.ambulance_id\u0014                 left join stretcher on stretcher.stretcher_id=ro.stretcher_id\u0014                 left join rescue_status_record cancel on cancel.rescue_id=ro.rescue_id and cancel.rescue_status=-1\u0014        where `cross`.parent_rescue_center_id=70 and `cross`.create_time BETWEEN '2026-05-31 00:00:00' and '2026-05-31 23:59:59' and rocr.tag_type is null";
        String sqlStr = MyStringUtils.replaceAllInvisibleChars(sql);
        String[] sqlTemplateV2 = AkOutputVisitorUtils.getSqlTemplate_v2(sqlStr, dbId);
        System.out.println(sqlTemplateV2[0]);
        System.out.println(sqlStr);
    }

    public void test_fix_cross_2(){
        // SQLSelectParser.  1577行
        String sql = "select\u0014            ro.rescue_id,\u0014            call_serial_num,\u0014            center_name,\u0014            call_name,\u0014            call_phone,\u0014            contact_phone,\u0014            ro.sex,\u0014            symptom,\u0014            ro.pt_oral_symptom oral_symptom,\u0014            special_case,\u0014            call_type.call_type_name,\u0014            rescue_address,\u0014            if( rocr.first_assign_time is null,-1,1) as dispatch_status,\u0014            if( rocr.first_assign_time is null,0,1) as ambulance_count,\u0014            ambulance.license_plate am_license_plate,\u0014            driver_name,\u0014            driver_telephone driver_phone,\u0014            doc_name doctor_name,\u0014            doctor.account doctor_phone,\u0014            stretcher.`name` assist_staff_name,\u0014            stretcher.telephone assist_staff_phone,\u0014            dispatcher.dispatcher_name,\u0014            rocr.first_assign_time assign_time,\u0014            ambulance_go_time,\u0014            ambulance_reach_time,\u0014            send_to_hos_time,\u0014            arrival_hospital_time arrival_time,\u0014            cancel.create_time cancel_time,\u0014            '' primary_diagnosis,\u0014            '' emergency_measures,\u0014            '' ecg_image\u0014        from cross_regional_rescue_bind `cross`\u0014                 inner join rescue_order_v2 ro on ro.rescue_id=`cross`.rescue_id\u0014                 inner join rescue_order_current_record rocr on ro.rescue_id=rocr.rescue_id\u0014                 inner join emergency_center center on center.center_id=ro.center_id\u0014                 inner join call_type on call_type.call_type_id=ro.call_type and call_type.rescue_type=1\u0014                 left join doctor on doctor.doc_id=ro.doc_id\u0014                 left join driver on driver.driver_id=ro.driver_id\u0014                 left join dispatcher on dispatcher.dispatcher_id=ro.dp_id\u0014                 left join ambulance on ambulance.ambulance_id=ro.ambulance_id\u0014                 left join stretcher on stretcher.stretcher_id=ro.stretcher_id\u0014                 left join rescue_status_record cancel on cancel.rescue_id=ro.rescue_id and cancel.rescue_status=-1\u0014        where `cross`.parent_rescue_center_id=70 and `cross`.create_time BETWEEN '2026-06-01 00:00:00' and '2026-06-01 23:59:59' and rocr.tag_type is null";
        String sqlStr = MyStringUtils.replaceAllInvisibleChars(sql);
        String[] sqlTemplateV2 = AkOutputVisitorUtils.getSqlTemplate_v2(sqlStr, dbId);
        System.out.println(sqlTemplateV2[0]);
        System.out.println(sqlStr);

    }


}
