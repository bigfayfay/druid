package com.ankki.druid.parser.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 样例数据（用于性能基准测试）
 *
 * <p>生成时间: 2026-05-16 11:33:05
 * <p>SQL 数量: 50
 */
public class BenchmarkSqlSamples {

    private static final String[] SQLS_WITH_PARAMS = new String[50];
    private static final Integer[] DB_TYPE_IDS = new Integer[50];

    static {

        SQLS_WITH_PARAMS[0] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[0] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[1] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[1] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[2] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[2] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[3] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[3] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[4] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[4] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[5] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[5] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[6] = "INSERT INTO ud.xfer_stat_20260512 xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?::character varyin...";
        DB_TYPE_IDS[6] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[7] = "UPDATE ud.xfer_statfile_record xfer_statfile_recordSET file_offset = $1::numeric(10, 0)WHERE ((file_name)::text = $2::text);;()";
        DB_TYPE_IDS[7] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[8] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[8] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[9] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[9] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[10] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[10] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[11] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[11] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[12] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[12] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[13] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[13] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[14] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[14] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[15] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[15] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[16] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[16] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[17] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[17] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[18] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[18] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[19] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[19] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[20] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[20] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[21] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[21] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[22] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[22] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[23] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[23] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[24] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[24] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[25] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[25] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[26] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[26] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[27] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[27] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[28] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[28] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[29] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[29] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[30] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[30] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[31] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[31] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[32] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[32] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[33] = "INSERT INTO ud.dr_gsm_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, opp_number, a_number, translated_number, start_time, duration	, call_type, video_type, fcitype, test_cdr_flag, c...";
        DB_TYPE_IDS[33] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[34] = "INSERT INTO ud.dr_ggprs_20260512 a	(service_id, dr_type, imsi, notify_type, user_number	, stop_time, start_time, duration, mns_type, test_cdr_flag	, imei, sequence_id, lac_id, cell_id, stop_cause	, or...";
        DB_TYPE_IDS[34] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[35] = "BEGIN;INSERT INTO DR_SMS_20260512 a	(HPLMN2, ADDUP_RES_USING_VAL, CALL_REFNUM, IMEI, MOC_BILL_TYPE	, MTC_BILL_TYPE, OPERATOR_CODE, OPP_GATEWAY, RESERVE1, RESERVE2	, RESERVE3, RESERVE4, RESERVE_FIELDS,...";
        DB_TYPE_IDS[35] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[36] = "INSERT INTO ud.de_stat_20260512 a	(original_file, starttime, date_time, proc_info, earlist	, endtime, errorstat, filestat, filterstat, latest	, newtbcgcount, nowritecount, readfilename, readfilesize, ...";
        DB_TYPE_IDS[36] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[37] = "INSERT INTO ud.de_stat_20260512 a	(original_file, starttime, date_time, proc_info, earlist	, endtime, errorstat, filestat, filterstat, latest	, newtbcgcount, nowritecount, readfilename, readfilesize, ...";
        DB_TYPE_IDS[37] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[38] = "INSERT INTO ud.de_stat_20260512 a	(original_file, starttime, date_time, proc_info, earlist	, endtime, errorstat, filestat, filterstat, latest	, newtbcgcount, nowritecount, readfilename, readfilesize, ...";
        DB_TYPE_IDS[38] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[39] = "INSERT INTO ud.de_stat_20260512 a	(original_file, starttime, date_time, proc_info, earlist	, endtime, errorstat, filestat, filterstat, latest	, newtbcgcount, nowritecount, readfilename, readfilesize, ...";
        DB_TYPE_IDS[39] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[40] = "INSERT INTO ud.de_stat_20260512 a	(original_file, starttime, date_time, proc_info, earlist	, endtime, errorstat, filestat, filterstat, latest	, newtbcgcount, nowritecount, readfilename, readfilesize, ...";
        DB_TYPE_IDS[40] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[41] = "INSERT INTO ud.de_stat_20260512 a	(original_file, starttime, date_time, proc_info, earlist	, endtime, errorstat, filestat, filterstat, latest	, newtbcgcount, nowritecount, readfilename, readfilesize, ...";
        DB_TYPE_IDS[41] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[42] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[42] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[43] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[43] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[44] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[44] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[45] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[45] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[46] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[46] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[47] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[47] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[48] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[48] = 3;  // 假设都是 Oracle

        SQLS_WITH_PARAMS[49] = "BEGIN;INSERT INTO xfer_stat_20260512	(srcfilename, srcpath, srcfilesize, srchost, destfilename	, destfilepath, destfilesize, midfilesize, desthost, datetime)VALUES (?, ?, ?::float8, ?, ?	, ?, ?::float...";
        DB_TYPE_IDS[49] = 3;  // 假设都是 Oracle

    }

    public static String[] getSqlSamples() {
        return SQLS_WITH_PARAMS;
    }

    public static Integer[] getDbTypeIds() {
        return DB_TYPE_IDS;
    }

    public static String getSqlSample(int index) {
        if (index >= 0 && index < SQLS_WITH_PARAMS.length) {
            return SQLS_WITH_PARAMS[index];
        }
        return null;
    }

    public static int getSampleCount() {
        return SQLS_WITH_PARAMS.length;
    }
}
