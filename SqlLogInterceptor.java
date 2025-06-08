package demo.api;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import io.netty.util.internal.ThrowableUtil;
import java.sql.Connection;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;

@Slf4j
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }) })
public class SqlLogInterceptor implements Interceptor {

  @Override
  @SuppressWarnings("all")
  public Object intercept(final Invocation invocation) throws Throwable {
    try {
      StatementHandler statementHandler = PluginUtils.realTarget(invocation.getTarget());
      MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
      // 如果是insert操作， 或者 @SqlParser(filter = true) 跳过该方法解析 ， 不进行验证
      MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
      System.out.println("SQL操作类型：" + mappedStatement.getSqlCommandType());
      BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
      Configuration configuration = mappedStatement.getConfiguration();
      String originalSql = boundSql.getSql();
      String sql = getSql(configuration, boundSql);
      log.info("原来的sql：" + originalSql);
      log.info("执行sql：" + sql);
    } catch (Exception e) {
      log.error("解析SQL出错，" + ThrowableUtil.stackTraceToString(e));
    }
    // 真正的去执行sql
    return invocation.proceed();
  }

  private static String getSql(Configuration configuration, BoundSql boundSql) {
    return showSql(configuration, boundSql);
  }

  private static String getParameterValue(Object obj) {
    String value = null;
    if (obj instanceof String) {
      value = "'" + obj.toString() + "'";
    } else if (obj instanceof Date) {
      DateFormat formatter = DateFormat.getDateTimeInstance(2, 2, Locale.CHINA);
      value = "'" + formatter.format(new Date()) + "'";
    } else if (obj != null) {
      value = obj.toString();
    } else {
      value = "";
    }

    return value;
  }

  private static String showSql(Configuration configuration, BoundSql boundSql) {
    Object parameterObject = boundSql.getParameterObject();
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
    if (CollectionUtils.isNotEmpty(parameterMappings) && parameterObject != null) {
      TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
	sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(parameterObject)));
      } else {
	MetaObject metaObject = configuration.newMetaObject(parameterObject);
	for (ParameterMapping parameterMapping : parameterMappings) {
	  String propertyName = parameterMapping.getProperty();
	  Object obj;
	  if (metaObject.hasGetter(propertyName)) {
	    obj = metaObject.getValue(propertyName);
	    sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(obj)));
	  } else if (boundSql.hasAdditionalParameter(propertyName)) {
	    obj = boundSql.getAdditionalParameter(propertyName);
	    sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(obj)));
	  } else {
	    sql = sql.replaceFirst("\\?", "缺失");
	  }
	}
      }
    }

    return sql;
  }

  @Override
  public Object plugin(final Object target) {
    if (target instanceof StatementHandler) {
      return Plugin.wrap(target, this);
    }
    return target;
  }

  @Override
  public void setProperties(Properties properties) {
    Interceptor.super.setProperties(properties);
  }

}