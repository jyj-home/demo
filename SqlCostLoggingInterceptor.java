package demo.api;

import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Intercepts({
    @Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class, RowBounds.class,
	ResultHandler.class }),
    @Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }),
    @Signature(type = Executor.class, method = "queryCursor", args = { MappedStatement.class, Object.class,
	RowBounds.class }) })
//@Component
public class SqlCostLoggingInterceptor implements Interceptor {

  private static final Logger logger = LoggerFactory.getLogger(SqlCostLoggingInterceptor.class);
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    long start = System.currentTimeMillis();
    Object result = invocation.proceed();
    long cost = System.currentTimeMillis() - start;

    Object[] args = invocation.getArgs();
    MappedStatement ms = (MappedStatement) args[0];
    Object param = args.length > 1 ? args[1] : null;
    BoundSql boundSql = ms.getBoundSql(param);
    String formattedSql = WHITESPACE.matcher(boundSql.getSql()).replaceAll(" ").trim();
    String method = ms.getId();

    String methodName = invocation.getMethod().getName();

    // 仅打印执行信息，不统计 cursor 遍历数量
    logger.debug("==> Mapper: {}", method);
    logger.debug("==> SQL: {}", formattedSql);

    if ("queryCursor".equals(methodName)) {
      logger.debug("<==      [Cursor 开启] [耗时: {}ms]", cost);
    } else {
      int resultSize = 0;
      if (result instanceof java.util.List<?>) {
	resultSize = ((java.util.List<?>) result).size();
      } else if (result != null) {
	resultSize = 1;
      }
      logger.debug("<==      Total: {} [耗时: {}ms]", resultSize, cost);
    }

    return result;
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(Properties properties) {
  }
}
