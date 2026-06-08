package com.ankki.druid.dialect.oracle;

import com.alibaba.druid.sql.SQLUtils;
import com.ankki.druid.parser.AkDbTypeEnum;
import com.ankki.druid.parser.template.AkOutputVisitorUtils;
import com.ankki.druid.util.MyStringUtils;
import junit.framework.TestCase;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

public class OracleInsertTest extends TestCase {


    public void test_for_issue() throws Exception {

        for (String sql : build_sqls()) {
            String[] sqlTemplateV2 = AkOutputVisitorUtils.getSqlTemplate_v2(MyStringUtils.replaceAllInvisibleChars(sql), AkDbTypeEnum.Oracle.getTypeId());
            System.out.println(sqlTemplateV2[0]);
            System.out.println(sqlTemplateV2[1]);
            System.out.println(sqlTemplateV2[2]);
            System.out.println(sqlTemplateV2[3]);
            if (sqlTemplateV2.length >= 5) {
                System.out.println(sqlTemplateV2[4]);
            }
            System.out.println("--------------------------------------------------");
        }
    }

    public List<String> build_sqls() {
        List<String> sqlList = new ArrayList<String>();
        sqlList.add("/* Traceid: a210cd1aa8374eb48cac4543fb727d1e */ select count(*)\u0014        from zcy_communication_voice_record\u0014          where send_time>='2026-06-01 00:02:00' and send_time<'2026-06-01 00:07:00' and call_duration >10 and reply_key =\u0012\u0012");
//        sqlList.add("");
        sqlList.add("select\u0014--         zspd.chcount,\u0014\t\t\t\tzg.f_applyno,\u0014        (zg.chmoney * -1) as chmoney,\u0014        NOW() as ENDLIQUIDATE,\u0014        zg.DETAILGUID as detailguid,\u0014        xmc.chitcode as chitcode,\u0014        xmc.chitclass,\u0014        xx.guid as xmguid,\u0014        xmc.initcode as initcode,\u0014        xmc.initname,\u0014        fnc.jczlguid as fncguid,\u0014        xmc.sectioncode as sectioncode,\u0014        xmc.sectionname,\u0014        zg.noticeno,\u0014        xmc.capitaldividemode,\u0014        xmc.countyregcode,\u0014        xmc.cityregcode,\u0014--         zj.guid as zs_guid,\u0014--         zsp.f_regicode,\u0014--         zsp.f_enteguid as fs_guid,\u0014--         zj.tradetype,\u0014        ( case when xmc.fundaccountedmode = '2' then g.F_ENTEGUID else xmc.fs_enterguid end ) as f_payenteguid\u0014        ,\u0014        zb.*,\u0014        IFNULL(a.jsje,0) as ajsje,\u0014        IFNULL(b.jsje,0) as bjsje,\u0014        IFNULL(c.jsje,0) as cjsje,\u0014        IFNULL(d.jsje,0) as djsje,\u0014        a.jsywdm as ajsywdm,\u0014        b.jsywdm as bjsywdm,\u0014        c.jsywdm as cjsywdm,\u0014        d.jsywdm as djsywdm,\u0014        a.zsjc as azsjc,\u0014        b.zsjc as bzsjc,\u0014        c.zsjc as czsjc,\u0014        d.zsjc as dzsjc,\u0014        a.sectioncode as asectioncode,\u0014        b.sectioncode as bsectioncode,\u0014        c.sectioncode as csectioncode,\u0014        d.sectioncode as dsectioncode,\u0014        a.zhlxdm as azhlxdm,\u0014        b.zhlxdm as bzhlxdm,\u0014        c.zhlxdm as czhlxdm,\u0014        d.zhlxdm as dzhlxdm,\u0014        a.jsmbzhdm as ajsmbzhdm,\u0014        b.jsmbzhdm as bjsmbzhdm,\u0014        c.jsmbzhdm as cjsmbzhdm,\u0014        d.jsmbzhdm as djsmbzhdm\u0014        from\u0014\t\t\t\tzg_applyforrefund g,\u0014\t\t\t\tzg_applyforrefunddetail zg,\u0014        (SELECT b.*  FROM\u0014        (select *,a.fs_guid as fs_guid1 from  xt_unit a where  a.chargelevel='1' and a.regicode='330100' GROUP BY a.fs_guid HAVING count(*)=1\u0014        union\u0014        select c.*,c.fs_guid as fs_guid1 from xt_unit c where c.fs_guid in (SELECT b.fs_guid FROM xt_unit b WHERE b.chargelevel = '1'  and b.REGICODE='330100' GROUP BY b.fs_guid HAVING count(*) >1)  and  c.chargelevel = '1'\u0014        and c.REGICODE= '330100' and enable='1'\u0014        ) a ,\u0014        zs_chitcode b\u0014        WHERE\u0014        a.fs_guid = b.FS_ENTERGUID\u0014        AND b.REGICODE = '330100'\u0014        AND a.REGICODE = b.REGICODE) xmc\u0014        left join zg_functionsection fnc on xmc.sectioncode = fnc.code and fnc.regicode = xmc.regicode and fnc.year = YEAR(NOW())\u0014        left join xm_incomeitem xx on xmc.initcode = xx.initcode and xx.year = YEAR(NOW())\u0014        left join\t(select p_id,zsjc,jsywdm,sectioncode,jsje,zhlxdm,jsmbzhdm from zg_jsfasdmxbtochit where zsjc = '中央') a on\txmc.id = a.p_id\u0014        left join\t(select p_id,zsjc,jsywdm,sectioncode,jsje,zhlxdm,jsmbzhdm from zg_jsfasdmxbtochit where zsjc = '省') b   on xmc.id = b.p_id\u0014        left join\t(select p_id,zsjc,jsywdm,sectioncode,jsje,zhlxdm,jsmbzhdm from zg_jsfasdmxbtochit where zsjc = '市') c   on xmc.id = c.p_id\u0014        left join\t(select p_id,zsjc,jsywdm,sectioncode,jsje,zhlxdm,jsmbzhdm from zg_jsfasdmxbtochit where zsjc = '县') d   on\txmc.id = d.p_id,\u0014        zg_jsfasdzb zb\u0014        where\u0014        g.regicode = '330100'\u0014        and zg.guid = '0a0cb8683af54361b856b0b0678372f1'\u0014        and g.applyno = zg.f_applyno\u0014        and zb.id = xmc.faid\u0014        and zg.chmoney <> 0\u0014        and zg.f_chitcode = xmc.chitcode\u0014        and CURDATE() <= zb.tyrq\u0014        and CURDATE() >= zb.qyrq");

        return sqlList;
    }



}
