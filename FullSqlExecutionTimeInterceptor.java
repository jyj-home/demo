package demo.api;

import java.lang.reflect.Field;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.List;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = { Statement.class, ResultHandler.class }),
    @Signature(type = StatementHandler.class, method = "queryCursor", args = { Statement.class }),
    @Signature(type = StatementHandler.class, method = "update", args = { Statement.class }),
    @Signature(type = StatementHandler.class, method = "batch", args = { Statement.class }) })
@Component
public class FullSqlExecutionTimeInterceptor implements Interceptor {
//  private static final Logger logger = LoggerFactory.getLogger(FullSqlExecutionTimeInterceptor.class);

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    long startTime = System.currentTimeMillis();
    try {
      return invocation.proceed();
    } finally {
      long endTime = System.currentTimeMillis();
      long executionTime = endTime - startTime;

      StatementHandler handler = (StatementHandler) invocation.getTarget();
      String methodName = invocation.getMethod().getName();
      MetaObject metaObject = SystemMetaObject.forObject(handler);
      MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
      String mapperId = mappedStatement.getId();
      String sql = getRealSql(mappedStatement.getConfiguration(), handler.getBoundSql());

      Logger logger = LoggerFactory.getLogger(mapperId);
      logger.info("<==      MethodName：{} Duration：{} ExecutionSQL：{}", methodName, executionTime, sql);
    }
  }

  private String getRealSql(Configuration configuration, BoundSql boundSql) {
    Object parameterObject = boundSql.getParameterObject();
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

    String sql = boundSql.getSql();
    if (!parameterMappings.isEmpty()) {
      TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      for (ParameterMapping mapping : parameterMappings) {
	String property = mapping.getProperty();
	Object value;
	if (boundSql.hasAdditionalParameter(property)) {
	  value = boundSql.getAdditionalParameter(property);
	} else if (parameterObject == null) {
	  value = null;
	} else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
	  value = parameterObject;
	} else {
	  Field field = parameterObject.getClass().getDeclaredField(property);
	  field.setAccessible(true);
	  value = field.get(parameterObject);
	}
	sql = sql.replaceFirst("\\?", formatParameter(value));
      }
    }
    return sql;
  }

  private String formatParameter(Object param) {
    if (param == null) {
      return null;
    }
    if (param instanceof String || param instanceof Character) {
      return "'" + param.toString() + "'";
    }
    if (param instanceof java.util.Date) {
      return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(param) + "'";
    }
    return param.toString();
  }

  private static String getRealSql(MappedStatement ms, Object parameterObject) {
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    String sql = boundSql.getSql();
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    Object[] parameters = new Object[parameterMappings.size()];
    Configuration configuration = ms.getConfiguration();
    TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();

    MetaObject metaObject = parameterObject != null ? configuration.newMetaObject(parameterObject) : null;

    for (int i = 0; i < parameterMappings.size(); i++) {
      ParameterMapping pm = parameterMappings.get(i);
      String propertyName = pm.getProperty();

      Object value;
      if (boundSql.hasAdditionalParameter(propertyName)) {
	value = boundSql.getAdditionalParameter(propertyName);
      } else if (parameterObject == null) {
	value = null;
      } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
	value = parameterObject;
      } else {
	value = metaObject.getValue(propertyName);
      }

      parameters[i] = value;
    }

    // 替换 ? 为参数值（只适用于展示目的，不可用于执行）
    for (Object param : parameters) {
      String val = param == null ? "null" : ("'" + param.toString() + "'");
      sql = sql.replaceFirst("\\?", val);
    }

    return sql;
  }
}
