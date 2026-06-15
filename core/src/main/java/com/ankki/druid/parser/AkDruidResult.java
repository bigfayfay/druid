/*
 * Decompiled with CFR 0.152.
 */
package com.ankki.druid.parser;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public class AkDruidResult {
    DbType dbType;
    AkSqlParserStatusEnum status;
    String template = "";
    String removeBindingParameterSql = "";
    String md5 = "";
    String firstOperType;
    List<String> tableName = new ArrayList<String>();
    List<String> tableNameAndOperType = new ArrayList<String>();
    List<String> fieldName = new ArrayList<String>();
    List<String> Conditions = new ArrayList<String>();
    List<Object> outParameterValue = new ArrayList<Object>();
    List<String> functions = new ArrayList<String>();
    Boolean sqlInject;
    String exceptionMsg;

    public Boolean getSqlInject() {
        return this.sqlInject;
    }

    public void setSqlInject(Boolean sqlInject) {
        this.sqlInject = sqlInject;
    }

    public DbType getDbType() {
        return this.dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public AkSqlParserStatusEnum getStatus() {
        return this.status;
    }

    public void setStatus(AkSqlParserStatusEnum status) {
        this.status = status;
    }

    public String getFirstOperType() {
        return this.firstOperType;
    }

    public void setFirstOperType(String firstOperType) {
        this.firstOperType = firstOperType;
    }

    public List<String> getTableName() {
        return this.tableName;
    }

    public void setTableName(List<String> tableName) {
        this.tableName = tableName;
    }

    public List<String> getTableNameAndOperType() {
        return this.tableNameAndOperType;
    }

    public void setTableNameAndOperType(List<String> tableNameAndOperType) {
        this.tableNameAndOperType = tableNameAndOperType;
    }

    public List<String> getFieldName() {
        return this.fieldName;
    }

    public void setFieldName(List<String> fieldName) {
        this.fieldName = fieldName;
    }

    public List<String> getConditions() {
        return this.Conditions;
    }

    public void setConditions(List<String> conditions) {
        this.Conditions = conditions;
    }

    public List<Object> getOutParameterValue() {
        return this.outParameterValue;
    }

    public void setOutParameterValue(List<Object> outParameterValue) {
        this.outParameterValue = outParameterValue;
    }

    public String getTemplate() {
        return this.template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getRemoveBindingParameterSql() {
        return this.removeBindingParameterSql;
    }

    public void setRemoveBindingParameterSql(String removeBindingParameterSql) {
        this.removeBindingParameterSql = removeBindingParameterSql;
    }

    public String getMd5() {
        return this.md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getExceptionMsg() {
        return this.exceptionMsg;
    }

    public void setExceptionMsg(String exceptionMsg) {
        this.exceptionMsg = exceptionMsg;
    }

    public List<String> getFunctions() {
        return this.functions;
    }

    public void setFunctions(List<String> functions) {
        this.functions = functions;
    }
}

