import com.ankki.druid.parser.AkDruidSqlParser;
import com.ankki.druid.parser.AkSqlParserStatusEnum;

public class DruidSqlParser {

    /**
     * 调用AkDruidSqlParser.getSqlTemplate方法，
     *
     * @param strSql
     * @param nDBtype
     * @return 返回Success或Failure
     */
    public static String ParseSql(String strSql, int nDBtype) {
        String sql = AkDruidSqlParser.getSqlTemplate(strSql, nDBtype, true);
        if (sql.contains(AkSqlParserStatusEnum.Success.name())) {
            return AkSqlParserStatusEnum.Success.name();
        } else {
            return AkSqlParserStatusEnum.Failure.name();
        }
    }

    /**
     * 调用AkDruidSqlParser.getSqlTemplate方法，
     *
     * @param strSql
     * @param nDBtype
     * @return 返回内容与AkDruidSqlParser.getSqlTemplate一致
     */
    public static String GetSqlFormat(String strSql, int nDBtype) {
        System.out.println("参数数据库类型：" + nDBtype);
        return AkDruidSqlParser.getSqlTemplate(strSql, nDBtype, true);
    }

    /**
     * 调用AkDruidSqlParser.getConditions方法
     *
     * @return 返回内容与AkDruidSqlParser.getConditions方法一致
     */
    public static String GetConditions() {
        return AkDruidSqlParser.getConditions();
    }

    /**
     * 调用AkDruidSqlParser.getFunctions方法
     *
     * @return 返回内容与AkDruidSqlParser.getFunctions方法一致
     */
    public static String GetFunctions() {
        return AkDruidSqlParser.getFunctions();
    }

    /**
     * 调用AkDruidSqlParser.getColumns方法
     *
     * @return 返回内容与AkDruidSqlParser.getColumns方法一致
     */
    public static String GetFields() {
        return AkDruidSqlParser.getColumns();
    }


    /**
     * 调用AkDruidSqlParser.getTables方法
     *
     * @return 返回内容与AkDruidSqlParser.getTables方法一致
     */
    public static String GetTables() {
        return AkDruidSqlParser.getTables();
    }


    /**
     * 调用AkDruidSqlParser.getParameterValues
     *
     * @return 返回内容与AkDruidSqlParser.getParameterValues方法一致
     */
    public static String GetValues() {
        return AkDruidSqlParser.getParameterValues();
    }


    /**
     * 调用AkDruidSqlParser.checkSqlInject方法
     *
     * @return 返回内容与AkDruidSqlParser.checkSqlInject方法一致
     */
    public static String CheckInject(String strSql, int nDBtype) {
        return AkDruidSqlParser.checkSqlInject(strSql, nDBtype);
    }
}
