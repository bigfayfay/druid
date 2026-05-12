package com.ankki.druid.parser.template;

import com.alibaba.druid.DbType;
import com.ankki.druid.parser.AkSqlParserStatusEnum;
import java.util.List;

public class AkTemplateResult {
  DbType dbType;
  
  Integer akDbTypeId;
  
  AkSqlParserStatusEnum status;
  
  String md5 = "";
  
  String template = "";
  
  List<Object> parameterValues;
  
  String sqlBind;
  
  String exceptionMsg;
  
  public DbType getDbType() {
    return this.dbType;
  }
  
  public void setDbType(DbType dbType) {
    this.dbType = dbType;
  }
  
  public Integer getAkDbTypeId() {
    return this.akDbTypeId;
  }
  
  public void setAkDbTypeId(Integer akDbTypeId) {
    this.akDbTypeId = akDbTypeId;
  }
  
  public AkSqlParserStatusEnum getStatus() {
    return this.status;
  }
  
  public void setStatus(AkSqlParserStatusEnum status) {
    this.status = status;
  }
  
  public String getMd5() {
    return this.md5;
  }
  
  public void setMd5(String md5) {
    this.md5 = md5;
  }
  
  public String getTemplate() {
    return this.template;
  }
  
  public void setTemplate(String template) {
    this.template = template;
  }
  
  public List<Object> getParameterValues() {
    return this.parameterValues;
  }
  
  public void setParameterValues(List<Object> parameterValues) {
    this.parameterValues = parameterValues;
  }
  
  public String getSqlBind() {
    return this.sqlBind;
  }
  
  public void setSqlBind(String sqlBind) {
    this.sqlBind = sqlBind;
  }
  
  public String getExceptionMsg() {
    return this.exceptionMsg;
  }
  
  public void setExceptionMsg(String exceptionMsg) {
    this.exceptionMsg = exceptionMsg;
  }
}
