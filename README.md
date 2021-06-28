[TOC]

# MyBatis源码学习

## 搭建MyBatis源码环境

1. 下载源码：https://github.com/mybatis/mybatis-3
2. mybatis的源码是maven工程，在编辑器进行导入
3. 把mybatis源码的pom文件```<optional>true</optional>```,全部改为false
4. 在工程目录下执行 ```mvn clean install -Dmaven.test.skip=true``` 将当前工程安装到本地仓库。
   附：（```mvn clean source:jar install -Dmaven.test.skip=true``` 可以将**源码**也同时安装到本地仓库）
5. 其它工程依赖此工程

## 为什么需要ORM框架

传统的JDBC编程存在的弊端：

- 工作量大，操作步骤至少需要5步
- 业务代码和技术代码相互耦合
- 连接资源需要手动关闭，带来了隐患

## MyBatis核心组件介绍

- Configuration：用于描述MyBatis的主配置信息，其他组件需要获取配置信息时，直接通过Configuration对象获取。除此之外，MyBatis在应用启动时，将Mapper配置信息、类型别名、TypeHandler等注册到Configuration组件中，其他组件需要这些信息时，也可以从Configuration对象中获取。
- MappedStatement：MappedStatement用于描述Mapper中的SQL配置信息，是对Mapper XML配置文件中<select|update|delete|insert>等标签或者@Select/@Update等注解配置信息的封装。
- SqlSession：SqlSession是MyBatis提供的面向用户的API，表示和数据库交互时的会话对象，用于完成数据库的增删改查功能。SqlSession是Executor组件的外观，目的是对外提供易于理解和使用的数据库操作接口。
- Executor：Executor是MyBatis的SQL执行器，MyBatis中对数据库所有的增删改查操作都是由Executor组件完成的。
- StatementHandler：StatementHandler封装了对JDBC Statement对象的操作，比如为Statement对象设置参数，调用Statement接口提供的方法与数据库交互，等等。
- ParameterHandler：当MyBatis框架使用的Statement类型为CallableStatement和PreparedStatement时，ParameterHandler用于为Statement对象参数占位符设置值。
- ResultSetHandler：ResultSetHandler封装了对JDBC中的ResultSet对象操作，当执行SQL类型为SELECT语句时，ResultSetHandler用于将查询结果转换成Java对象。
- TypeHandler：TypeHandler是MyBatis中的类型处理器，用于处理Java类型与JDBC类型之间的映射。它的作用主要体现在能够根据Java类型调用PreparedStatement或CallableStatement对象对应的setXXX()方法为Statement对象设置值，而且能够根据Java类型调用ResultSet对象对应的getXXX()获取SQL执行结果。

## MyBatis的整体架构

接口层：SqlSession

核心处理层：配置解析、参数映射、SQL解析、SQL执行、结果集映射、插件

基础支撑层：数据源、事务管理、缓存、Binding模块、反射、类型转换、日志模块、资源加载、解析器

![image-20210616191218241](/Users/smzdm/Library/Application Support/typora-user-images/image-20210616191218241.png)

## MyBatis的核心流程

1. 读取XML配置文件和注解中的配置信息，创建配置对象，并完成各个模块的初始化工作**（初始化阶段）**
2. 封装iBatis的编程模型，使用mapper接口开发的初始化工作**（代理阶段）**
3. 通过SqlSession完成SQL的解析，参数的映射，SQL的执行，结果的解析过程**（数据读写阶段）**

## 设计模式的几个原则

- 单一职责原则
- 依赖倒置原则：高层模块不直接依赖于低层模块的具体实现，解藕高层与低层，即面向接口编程
- 开放-封闭原则

## 源码分析

### MyBatis启动流程源码分析

MyBatis框架启动时，会对所有的配置信息进行解析，然后将解析后的内容注册到Configuration对象的这些属性中。（MyBatis的初始化，就是建立一个Configuration对象。Configuration对象加载到内存，在内存中各个配置都有一个实体进行对应。）

#### 用到的设计模式：

##### 工厂方法模式

Configuration组件作为Executor、StatementHandler、ResultSetHandler、ParameterHandler组件的工厂类，用于创建这些组件的实例。Configuration类中提供了这些组件的工厂方法，例如，Executor组件有4种不同的实现，分别为BatchExecutor、ReuseExecutor、SimpleExecutor、CachingExecutor，当defaultExecutorType的参数值为REUSE时，newExecutor()方法返回的是ReuseExecutor实例，当参数值为SIMPLE时，返回的是SimpleExecutor实例，这是典型的工厂方法模式的应用。

##### 建造者模式

XMLConfigBuilder、XMLMapperBuilder、XMLStatementBuilder这三个Builder看上去不是流式风格，不像建造者模式，但是用到了建造者模式的设计思想，核心目的都是为了创建Configuration对象。

#### 源码分析的入口

```java
@Before
public void init() throws IOException {
    String resource = "mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    // 1.读取mybatis配置文件创建 sqlSessionFactory
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
}
```

Configuration：

- XMLConfigBuilder：主要负责解析mybatis-config.xml
- XMLMapperBuilder：主要负责解析映射配置文件
- XMLStatementBuilder：主要负责解析映射配置文件中的SQL节点

#### 映射器的核心类

- Configuration：MyBatis启动初始化的核心就是将所有xml配置文件信息加载到Configuration对象中，Configuration是单例的，生命周期是应用级的
- MapperRegistry：mapper接口动态代理工厂类的注册中心。在MyBatis中，通过mapperProxy实现InvocationHandler接口，MapperProxyFactory用于生成动态代理的实例对象，MyBatis框架在应用启动时会解析所有的Mapper接口，然后调用MapperRegistry对象的addMapper()方法将Mapper接口信息和对应的MapperProxyFactory对象注册到MapperRegistry对象中。
- ResultMap：用于解析mapper.xml中的ResultMap节点，使用ResultMapping来封装id、result等子元素
- MappedStatment：用于存储mapper.xml文件中的select、insert、update和delete语句，同时还包含了这些节点的很多重要属性。

  SQL配置有两种方式：

    - 通过XML文件配置；
    - 是通过Java注解，注解方式的（@Insert,@Select）不推介用，可读性差。

- SqlSource：mapper.xml的sql语句会被解析成SqlSource对象，经过解析SqlSource包含的语句最终仅包含 ？占位符，可以直接提交给数据库执行

### SqlSession创建源码分析

SqlSession是MyBatis对外提供的最关键的接口，通过它可以执行数据库读写命令、获取映射器、管理事务等

#### 用到的设计模式

- 策略模式：SqlSession中的策略模式体现在：创建数据源有3种方式，POOL、UNPOOL和JNDI。在创建Sqlsession的时候，是根据环境创建的，在Environment里面会指定数据源的方式，对于Sqlsession的使用代码来说，不管底层是如何创建Sqlsession的，都没有关系。只需要改配置，数据源模块就能够生产出3种不同类型的SqlSession。
- 外观模式：SqlSession是Executor组件的外观，目的是为用户提供更友好的数据库操作接口，这是设计模式中外观模式的典型应用。真正执行SQL操作的是Executor组件，Executor可以理解为SQL执行器。
- 装饰器模式：MyBatis支持一级缓存和二级缓存，当MyBatis开启了二级缓存功能时，会使用CachingExecutor对SimpleExecutor、ResueExecutor、BatchExecutor进行装饰，为查询操作增加二级缓存功能，这是装饰器模式的应用。
- 模板方法模式：BaseStatementHandler是一个抽象类，封装了通用的处理逻辑及方法执行流程，具体方法的实现由子类完成，这里使用到了设计模式中的模板方法模式。
- 工厂模式：MyBatis中的SqlSession实例使用工厂模式创建，所以在创建SqlSession实例之前需要先创建SqlSessionFactory工厂对象，然后调用SqlSessionFactory对象的openSession()方法。
- 享元模式：ReuseExecutor 可重用的执行器，重用的对象是Statement，这是享元思想的应用。
- 动态代理模式：MapperProxy使用的是JDK内置的动态代理，实现了InvocationHandler接口，invoke()方法中为通用的拦截逻辑。
- 静态代理模式：RoutingStatementHandler使用静态代理模式，根据上下文决定生成哪一个具体实现类。
- 建造者模式：SqlSessionFactoryBuilder读取配置信息创建SqlSessionFactory，建造者模式，方法级别生命周期。

#### Executor组件分析：

Executor是MyBatis核心接口之一，定义了数据库操作最基本的方法，SqlSession的功能都是基于它来实现的。

Executor的三个实现类解读：

- SimpleExecutor是最简单的执行器，根据对应的sql直接执行即可，不会做一些额外的操作；默认配置，每次使用都要创建新的**PrepareStatement**。
- BatchExecutor执行器，顾名思义，通过批量操作来优化性能。通常需要注意的是批量更新操作，由于内部有缓存的实现，使用完成后记得调用`flushStatements`来清除缓存。
- ReuseExecutor 可重用的执行器，重用的对象是Statement，也就是说该执行器会缓存同一个sql的Statement，省去Statement的重新创建，优化性能。内部的实现是通过一个HashMap来维护Statement对象的。由于当前Map只在该session中有效，所以使用完成后记得调用`flushStatements`来清除Map。

#### StatementHandler组件分析：

StatementHandler完成MyBatis最核心的工作，也是Executor实现的基础；
功能包括：创建statement对象、为sql语句绑定参数、执行增删改查等sql语句、将结果集进行转化

StatementHandler接口的实现大致有四个，其中三个实现类都是和JDBC中的Statement响对应的：
- SimpleStatementHandler，这个很简单了，就是对应我们JDBC中常用的Statement接口，用于简单SQL的处理；
- PreparedStatementHandler，这个对应JDBC中的PreparedStatement，预编译SQL的接口；
- CallableStatementHandler，这个对应JDBC中CallableStatement，用于执行存储过程相关的接口；
- RoutingStatementHandler，这个接口是以上三个接口的路由，没有实际操作，只是负责上面三个StatementHandler的创建及调用。

### SqlSession执行Mapper过程源码分析

首先创建SqlSessionFactory，通过SqlSessionFactory创建 SqlSession，这时候我们就可以通过SqlSession进行一些对数据库方面的操作，然后调用session对数 据库的事务进行一些提交，最后session.close()方法关闭数据库。

```
连接用完后要close，因为数据库连接是很宝贵的，你如果不关闭的，一直占用着连接，数据库就无法为其它的服务了
```

#### Mapper接口的注册过程

```java
@Test
public void quickStart(){
    // 2.获取sqlSession
    SqlSession sqlSession = sqlSessionFactory.openSession();
//        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
    // 3.获取对应mapper
    TUserMapper userMapper = sqlSession.getMapper(TUserMapper.class);
    // 4.执行查询语句并返回结果
    TUser user = userMapper.selectByPrimaryKey(1);
    System.out.println(user.toString());
}
```

TUserMapper是一个接口，我们调用SqlSession对象getMapper()返回的到底是什么呢我们知道，接口中定义的方法必须通过某个类实现该接口，然后创建该类的实例，才能通过实例调用方法。所以SqlSession对象的getMapper()方法返回的一定是某个类的实例。具体是哪个类的实例呢？实际上getMapper()方法返回的是一个动态代理对象。MyBatis中通过MapperProxy类实现动态代理。

Mybatis最大的提升就是用mapper接口编程，实现方式就是动态代理。

下面是MapperProxy类的关键代码：

```java
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  /**
   * 记录关联的sqlSession对象
   */
  private final SqlSession sqlSession;
  /**
   * mapper接口对应的class对象
   */
  private final Class<T> mapperInterface;
  /**
   * key是mapper接口中某个方法的Method对象，value是MapperMethod，
   * MapperMethod不记录任何状态信息，所以可以在多个代理对象共享
   */
  private final Map<Method, MapperMethod> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 如果是Object本身的方法，不增强，比如hashCode、toString不需要增强
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      } else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
    // 从缓存中读取mapperMethod对象，没有则添加
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    // 执行sql
    return mapperMethod.execute(sqlSession, args);
  }
  ...
}
```

相当于下面的代码（这也是ibatis的编程模型 ，面向sqlSession编程）：

```java
@Test
public void quickStart1(){
  // 2.获取sqlSession
  SqlSession sqlSession = sqlSessionFactory.openSession();
  // 3.执行查询语句并返回结果
  TUser user = sqlSession.selectOne("com.sxw.mapper.TUserMapper.selectByPrimaryKey", 1);
  System.out.println(user.toString());
}
```

实际上上面动态代理的翻译过程整体上就是以下步骤：

- 找到session中的对应的方法执行

  ```
  MapperMethod.SqlCommand.type + MapperMethod.MethodSignature.returnType
  ```

- 找到命名空间和方法名

  ```
  MapperMethod.SqlCommand.name + MapperMethod.MethodSignature.returnType
  ```

- 传递参数

  ```
  MapperMethod.MethodSignature.paramNameResolver
  ```

  上述代码存在的问题：强耦合，因此使用代理，Java语言中比较常用的实现动态代理的方式有两种，即JDK内置动态代理和CGLIB动态代理。

#### MyBatis的执行过程

1. 通过SqlSessionFactoryBuilder 解析mybatis-config.xml 文件中的 Configuration 创建一个 sqlSessionFactory
2. 通过sqlSessionFactory 创建一个SqlSession
3. SqlSession 调用一个 quary(查询)Executor执行器，是为了执行sql语句
4. 创建一个新的StatementHandler (参数执行器)
5. 调用ResultSetHandler (结果器返回结果)，执行SQl语句,不同的SQl语句返回不同的结果

#### 源码执行步骤

1. 在DefaultSqlSession的selectList()方法中，首先根据Mapper的Id从Configuration对象中获取对应的MappedStatement对象，然后以MappedStatement对象作为参数，调用Executor实例的query()方法完成查询操作。

   ```java
   public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
       try {
         // 从configuration中获取要执行的sql语句的配置信息
         MappedStatement ms = configuration.getMappedStatement(statement);
         // 通过executor执行语句，并返回指定的结果集
         return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER);
       } catch (Exception e) {
         throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
       } finally {
         ErrorContext.instance().reset();
       }
     }
   ```

2. 在BaseExecutor类的query()方法中，首先从MappedStatement对象中获取BoundSql对象，BoundSql类中封装了经过解析后的SQL语句及参数映射信息。然后创建CacheKey对象，该对象用于缓存的Key值。

   ```java
   public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
       // 获取BoundSql对象，BoundSql是对动态sql解析生成之后的sql语句和参数映射信息的封装
       BoundSql boundSql = ms.getBoundSql(parameter);
       // 创建CacheKey，用于缓存key
       CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
     	// 调用重载的query()方法
       return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }
   ```

3. 调用重载的query()方法，首先从MyBatis一级缓存中获取查询结果，如果缓存中没有，则调用BaseExecutor类的queryFromDatabase()方法从数据库中查询，关键代码如下：

   ```java
   public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
       ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
       ...
       List<E> list;
       try {
         queryStack++;
         // 首先从MyBatis一级缓存中获取查询结果
         list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
         if (list != null) {
           handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
         } else {
           list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
         }
       } finally {
         queryStack--;
       }
       ...
       return list;
     }
   ```

4. 调用BaseExecutor类的queryFromDatabase()方法从数据库中查询。queryFromDatabase()方法代码如下：

   ```java
   private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
       List<E> list;
       // 在缓存中添加占位符
       localCache.putObject(key, EXECUTION_PLACEHOLDER);
       try {
         list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
       } finally {
         localCache.removeObject(key);
       }
       // 添加到一级缓存
       localCache.putObject(key, list);
       if (ms.getStatementType() == StatementType.CALLABLE) {
         localOutputParameterCache.putObject(key, parameter);
       }
       return list;
     }
   ```

5. 调用doQuery()方法进行查询，然后将查询结果进行缓存，doQuery()是一个模板方法，由BaseExecutor子类实现。doQuery()方法代码如下：

   ```java
   public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
       Statement stmt = null;
       try {
         Configuration configuration = ms.getConfiguration();
         // 创建StatementHandler对象,newStatementHandler()方法返回的是RoutingStatementHandler的实例。
         StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
         // 创建JDBC中的Statement对象，然后为Statement对象设置参数操作
         stmt = prepareStatement(handler, ms.getStatementLog());
         return handler.query(stmt, resultHandler);
       } finally {
         closeStatement(stmt);
       }
     }
   ```

#### 查询方法调用链

```java
TUser user = userMapper.selectByPrimaryKey(1);

MapperProxy.invoke(Object proxy, Method method, Object[] args)

MapperMethod.execute(SqlSession sqlSession, Object[] args)

DefaultSqlSession.selectOne(String statement, Object parameter)

DefaultSqlSession.selectList(String statement, Object parameter)

DefaultSqlSession.selectList(String statement, Object parameter, RowBounds rowBounds)
  
CachingExecutor.query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler)
  
CachingExecutor.query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)  
  
BaseExecutor.query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
  
BaseExecutor.queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)  
  
SimpleExecutor.doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)

SimpleExecutor.prepareStatement(StatementHandler handler, Log statementLog)  
  
SimpleExecutor.getConnection(Log statementLog)  
  
JdbcTransaction.getConnection()
  
JdbcTransaction.openConnection()
  
PooledDataSource.getConnection()
  
PooledDataSource.popConnection(String username, String password)
  
PreparedStatementHandler.query(Statement statement, ResultHandler resultHandler) 
  
RoutingStatementHandler.query(Statement statement, ResultHandler resultHandler)
  
PreparedStatementHandler.query(Statement statement, ResultHandler resultHandler)
  
PreparedStatementLogger.invoke(Object proxy, Method method, Object[] params)
  
ClientPreparedStatement.execute()
```

无论调用的是SqlSession中的哪一个方法，最终都是走到了

```
<E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;
```

一次SqlSession就代表着一次数据库连接

### 日志模块源码分析

#### 用到的设计模式

- 适配器模式：在JAVA开发中，常用的日志框架有Log4j、Log4j2、java.util.logging、slf4j等，这些工具对外的接口不尽相同，为了统一这些工具的接口，MyBatis定义了一套统一的日志接口供上层使用。即用到了适配器模式。
- 代理模式：在日志模块的jdbc包下，包含很多个类，他们对JDBC的几个核心类进行的动态代理增强，变成了具备日志打印功能的类，我们看看StatementLogger，他是具备日志打印功能的Statement。
- 工厂模式：使用LogFactory获取Log实例。

#### 核心代码

LogFactory:

```java
/**
 * MyBatis查找日志框架的顺序为SLF4J→JCL→Log4j2→Log4j→JUL→No Logging。
 * 如果Classpath下不存在任何日志框架，则使用NoLoggingImpl日志实现类，即不输出任何日志
 */
static {
    tryImplementation(LogFactory::useSlf4jLogging);
    tryImplementation(LogFactory::useCommonsLogging);
    tryImplementation(LogFactory::useLog4J2Logging);
    tryImplementation(LogFactory::useLog4JLogging);
    tryImplementation(LogFactory::useJdkLogging);
    tryImplementation(LogFactory::useNoLogging);
}
private static void tryImplementation(Runnable runnable) {
    /**
     * logConstructor为空才执行，当已经匹配到日志实现时，后面的就不会继续执行
     */
    if (logConstructor == null) {
      try {
        runnable.run();
      } catch (Throwable t) {
        // ignore
      }
    }
}
private static void setImplementation(Class<? extends Log> implClass) {
    try {
      // 获取日志实现类的Constructor对象
      Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
      Log log = candidate.newInstance(LogFactory.class.getName());
      if (log.isDebugEnabled()) {
        log.debug("Logging initialized using '" + implClass + "' adapter.");
      }
      // 设置logConstructor,一旦设上，表明找到相应的log的jar包了，那后面别的log就不找了。
      logConstructor = candidate;
    } catch (Throwable t) {
      throw new LogException("Error setting Log implementation.  Cause: " + t, t);
    }
 }
```

### 数据源模块源码分析

常见的数据源组件都实现了DataSource的接口

MyBatis不但要能集成第三方的数据源组件，自身也提供了数据源的实现。

一般情况下，数据源的初始化过程参数很大，很麻烦，这时候工厂模式就派上用场

#### 工厂模式

- 工厂接口（SmallMovieFactory）
- 具体工厂类（SimpleSmallMovieFactory）
- 产品接口（SmallMovie）
- 具体接口类（CangSmallMovie）

#### MyBats中工厂模式的应用

- DataSourceFactory
- UnpooledDataSourceFactory

#### PooledDataSource源码解析

##### 获取connection

```java
private PooledConnection popConnection(String username, String password) throws SQLException {
  boolean countedWait = false;
  PooledConnection conn = null;
  // 记录尝试获取连接的起始时间戳
  long t = System.currentTimeMillis();
  // 初始化获取到连接的无效次数
  int localBadConnectionCount = 0;

  while (conn == null) {
    // 获取连接是同步的
    synchronized (state) {
      // 检测是否有空闲连接
      if (!state.idleConnections.isEmpty()) {
        // Pool has available connection， 有空闲连接则直接使用
        conn = state.idleConnections.remove(0);
        if (log.isDebugEnabled()) {
          log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
        }
      } else {// 进入 else 没有空闲连接
        // Pool does not have available connection
        // 判断活跃连接池中的数量是否大于最大连接数
        if (state.activeConnections.size() < poolMaximumActiveConnections) {
          // Can create new connection 没有超过最大连接的数量，创建一个新的连接
          conn = new PooledConnection(dataSource.getConnection(), this);
          if (log.isDebugEnabled()) {
            log.debug("Created connection " + conn.getRealHashCode() + ".");
          }
        } else {// 如果已经等于最大连接数，则不能创建新连接
          // Cannot create new connection
          // 没有空闲连接，获取最早创建的连接，这里隐含的一个逻辑是最早创建的连接如果没超时，那么在它之后创建的连接肯定也没超时
          PooledConnection oldestActiveConnection = state.activeConnections.get(0);
          long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
          if (longestCheckoutTime > poolMaximumCheckoutTime) {// 检测是否已经超过最长使用时间
            // Can claim overdue connection
            // 如果超时，对超时的信息进行统计
            state.claimedOverdueConnectionCount++;// 超时连接次数加一
            state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;// 看state中的注释
            state.accumulatedCheckoutTime += longestCheckoutTime;// 看state中的注释
            state.activeConnections.remove(oldestActiveConnection);// 从活跃的队列中移除
            if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {// 如果超时连接未提交，手动回滚
              try {
                oldestActiveConnection.getRealConnection().rollback();// 手动回滚
              } catch (SQLException e) {
                /*
                   Just log a message for debug and continue to execute the following
                   statement like nothing happened.
                   Wrap the bad connection with a new PooledConnection, this will help
                   to not interrupt current executing thread and give current thread a
                   chance to join the next competition for another valid/good database
                   connection. At the end of this loop, bad {@link @conn} will be set as null.
                 */
                log.debug("Bad connection. Could not roll back");
              }
            }
            // 在连接池创建新的连接，注意对于数据库来说，并没有创建新的连接，
            // 我的理解：如果真实的数据库连接还没有达到超时的限制，那么这个oldestActiveConnection.getRealConnection()仍然还是可用的
            conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
            conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
            conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());
            // 让老连接失效
            oldestActiveConnection.invalidate();
            if (log.isDebugEnabled()) {
              log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
            }
          } else {
            // Must wait
            // 无空闲连接，最早创建的连接没有失效，无法创建新的连接，只能阻塞
            try {
              if (!countedWait) {
                state.hadToWaitCount++;
                countedWait = true;
              }
              if (log.isDebugEnabled()) {
                log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
              }
              long wt = System.currentTimeMillis();
              // 阻塞等待指定时间 在回收的时候会唤醒
              state.wait(poolTimeToWait);
              state.accumulatedWaitTime += System.currentTimeMillis() - wt;
            } catch (InterruptedException e) {
              break;
            }
          }
        }
      }
      // 上面的循环跳出while循环了，那就是拿到数据库连接了
      if (conn != null) {
        // ping to server and check the connection is valid or not
        // 至此，获取连接成功，继续检查连接是否有效，同时更新统计数据
        if (conn.isValid()) {
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
          conn.setCheckoutTimestamp(System.currentTimeMillis());
          conn.setLastUsedTimestamp(System.currentTimeMillis());
          state.activeConnections.add(conn);
          state.requestCount++;
          state.accumulatedRequestTime += System.currentTimeMillis() - t;
        } else {// 连接无效
          if (log.isDebugEnabled()) {
            log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
          }
          state.badConnectionCount++;
          localBadConnectionCount++;
          conn = null;
          // 拿到无效连接，但如果没有超过重试的次数，允许再次尝试获取连接，否则抛出异常
          if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
            if (log.isDebugEnabled()) {
              log.debug("PooledDataSource: Could not get a good connection to the database.");
            }
            throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
          }
        }
      }
    }

  }

  if (conn == null) {
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }
    throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
  }

  return conn;
}
```

##### 归还connection

```java
/**
 * 回收线程资源
 * @param conn
 * @throws SQLException
 */
protected void pushConnection(PooledConnection conn) throws SQLException {
  // 回收连接的操作必须是同步的
  synchronized (state) {
    state.activeConnections.remove(conn);
    // isValid() 其中有一项校验是去 pingConnection
    if (conn.isValid()) {
      // 判断闲置连接池资源是否已达上限
      if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
        // 没有达到上限，进行回收
        state.accumulatedCheckoutTime += conn.getCheckoutTime();
        if (!conn.getRealConnection().getAutoCommit()) {
          // 如果还有事务没有提交，进行回滚
          conn.getRealConnection().rollback();
        }
        // 基于该连接，创建一个新的Connection，加入到idle列表，并刷新连接状态
        PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
        state.idleConnections.add(newConn);
        newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
        newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
        // 老连接失效
        conn.invalidate();
        if (log.isDebugEnabled()) {
          log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
        }
        // 通知其他线程可以来抢connection了
        state.notifyAll();
      } else {//如果空闲连接池已达上限，将连接真实关闭
        state.accumulatedCheckoutTime += conn.getCheckoutTime();
        if (!conn.getRealConnection().getAutoCommit()) {
          // learn:
          conn.getRealConnection().rollback();
        }
        // 关闭真实的数据库连接
        conn.getRealConnection().close();
        if (log.isDebugEnabled()) {
          log.debug("Closed connection " + conn.getRealHashCode() + ".");
        }
        // 将连接设置为失效
        conn.invalidate();
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
      }
      state.badConnectionCount++;
    }
  }
}
```

#### 驱动加载

> 为什么Class.forName("com.mysql.jdbc.Driver")后，驱动就被注册到DriverManager？

```java
package com.mysql.cj.jdbc;

import java.sql.DriverManager;
import java.sql.SQLException;

public class Driver extends NonRegisteringDriver implements java.sql.Driver {
    public Driver() throws SQLException {
    }

    static {
        try {
            DriverManager.registerDriver(new Driver());
        } catch (SQLException var1) {
            throw new RuntimeException("Can't register driver!");
        }
    }
}
```

查看MySQL驱动源码，可知在加载驱动的时候会执行静态代码块，将驱动加载到DriverManager。

也就是，在Class.forName加载完驱动类，开始执行静态初始化代码时，会自动新建一个Driver的对象，并调用DriverManager.registerDriver把自己注册到DriverManager中去。用Class.forName也是为了注册这个目的/(直接把Driver对象new出来，也是可以连接的，但是浪费空间没必要)。

#### 核心类

1. PooledConnection：使用动态代理封装了真正的数据库连接对象
2. PoolState：用于管理PooledConnection对象状态的组件，通过两个list分别管理空闲状态的连接资源和活跃状态的连接资源
3. PooledDataSource：一个简单的，同步的，线程安全的数据库连接池

### 缓存模块源码分析

MyBatis包含一个非常强大查询缓存特性，使用缓存可以使应用更快的获取数据，避免了频繁的数据库交互。MyBatis提供了一级缓存和二级缓存，其中一级缓存基于SqlSession实现，而二级缓存基于Mapper实现。MyBatis的缓存是基于map实现的，从缓存里面读取数据是缓存模块的核心基础功能。

#### 一级缓存

一级缓存默认开启，一级缓存存在于sqlSession的生命周期内

#### 二级缓存

存在于SqlSessionFactory的生命周期中，可以理解为跨sqlSession，缓存是以namespace为单位
setting参数cacheEnabled，这个参数是二级缓存的全局开关
要使用还需要配置

```<cache eviction = "FIFO" flushInterval="60000" size = "512" readonly="true">```

二级缓存容易出现脏读，不建议使用

##### 二级缓存为什么出现脏读？

一条数据被多个SqlSession查询，然后缓存到了各自的namespace中，其中一个SqlSession对数据做了修改，其它的SqlSession是感知不到的，所以其它namespace空间中缓存的数据就是脏数据。

虽然一级缓存也会出现脏读，但是问题不严重：

- 有事务的控制，如果是在事务的周期之内，别的线程是修改不了的
- 缓存的实效性很短， 二级缓存的生命周期很长

在两个namespace下都有同一份数据的副本，其中一个namespace对数据修改之后，另一个namespace就会出现脏读（关联查询容易出现这个问题）

MyBatis除了核心功能之外，有很多的附加功能，如：防止缓存击穿，添加缓存清空策略、序列化能力、日志能力、定时清空能力，附加功能可以以任意的组合附加到核心功能之上。

附加功能可以任意组合附加到核心功能至上，**那么怎么样优雅的为核心功能添加附加能力？**

> 可以使用：动态代理、继承

但是继承的方式是静态的，用户不能控制增加行为的方式和时机，另外新功能的组合存在多种组合，使用继承可能会使得大量子类出现。

可以通过装饰器模式来解决上面继承出现的痛点

> 装饰器模式是用来替换继承的一种技术，无需继承增加子类就能扩展对象的新功能。使用对象的关联关系代替继承关系，更加灵活，同时避免类型体系的快速膨胀。

#### 用到的设计模式-装饰器模式

装饰器模式（Decorator Pattern）允许向一个现有的对象添加新的功能，同时又不改变其结构。这种类型的设计模式属于结构型模式，它是作为现有的类的一个包装。

这种模式创建了一个装饰类，用来包装原有的类，并在保持类方法签名完整性的前提下，提供了额外的功能。

设计的模块：

- 组件（Component）：组件接口定义了**全部组件类和装饰器**的行为
- 组件实现类（ConcreteComponent）：实现Component接口，组件实现类就是被装饰器装饰的原始对象，新功能或者附加功能都是通过装饰器添加该类的对象上的。
- 装饰器抽象类（Decorator）：实现Component接口的抽象类，在其中封装了一个Component对象，也就是被装饰的对象。
- 具体装饰器类（ConcreteDecorator）：该实现类要向被装饰的对象添加某些功能。

**优点：**扩展性、灵活性比较强。装饰类和被装饰类可以独立发展，不会相互耦合，装饰模式是继承的一个替代模式，装饰模式可以动态扩展一个实现类的功能。

**缺点：**多层装饰比较复杂。

*JAVA中的IO就是典型的装饰器模式的应用*

#### 装饰器模式在MyBatis缓存模块中的实际应用

- Cache：缓存模块的核心接口，定义了缓存的基本功能
- PerpetualCache：在缓存模块中扮演ConcreteComponent角色，使用HashMap实现相关操作。
- BlockingCache：阻塞版本的缓存装饰器，保证只有一个线程到数据库去查找指定key对应的数据

#### 核心模块

- Cache：Cache接口是缓存模块的核心接口，定义了缓存模块的基本操作
- PerpetualCache：在缓存模块中扮演ConcreteComponent角色，使用HashMap来实现cache的相关操作
- BlockingCache：阻塞版本的缓存装饰器，保证只有一个线程到数据库去查找指定key对应的数据，以下为锁的粒度：

    - 粗粒度：所有的请求用一把锁，安全，但是有效率问题
    - 细粒度（按key）
      ```java
      public Object getObject(Object key) {
         // 先获取锁，，获取成功加锁，获取失败阻塞一段时间重试
         acquireLock(key);
         Object value = delegate.getObject(key);
         // 获取数据成功，释放锁
         if (value != null) {
           releaseLock(key);
         }
         return value;
      }
      ```
- CacheKey：MyBatis涉及到动态SQL的原因，缓存项的key不能仅仅通过一个String来表示，所以通过CacheKey来封装key值，CacheKey可以封装多个影响缓存key的因素

  构成CacheKey的对象：

    1. mapperStatement的id
    2. 指定查询集的范围（分页信息）
    3. 查询所使用的SQL语句
    4. 用户传递给SQL语句的实际参数值

#### 源码重点解读方法

- update(Object object)

```java
public void update(Object object) {
  int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

  count++;
  checksum += baseHashCode;
  baseHashCode *= count;

  hashcode = multiplier * hashcode + baseHashCode;

  updateList.add(object);
}
```

- equals(Object object)

```java
public boolean equals(Object object) {
    // 判断是否为同一对象
    if (this == object) {
      return true;
    }
    // 判断是否类型一致
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    /**
     * 一下判断减少hash碰撞
     */
    // hashcode是否相等
    if (hashcode != cacheKey.hashcode) {
      return false;
    }
    // checksum是否相等
    if (checksum != cacheKey.checksum) {
      return false;
    }
    // count是否相等
    if (count != cacheKey.count) {
      return false;
    }
    // 以上都相同，再继续判断updateList中的元素是否一致
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }
```

**生成CacheKey是BaseExecutor的createCacheKey方法，源码如下：**

```java
public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
  if (closed) {
    throw new ExecutorException("Executor was closed.");
  }
  CacheKey cacheKey = new CacheKey();
  cacheKey.update(ms.getId());
  cacheKey.update(rowBounds.getOffset());
  cacheKey.update(rowBounds.getLimit());
  cacheKey.update(boundSql.getSql());
  List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
  TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
  // mimic DefaultParameterHandler logic
  for (ParameterMapping parameterMapping : parameterMappings) {
    if (parameterMapping.getMode() != ParameterMode.OUT) {
      Object value;
      String propertyName = parameterMapping.getProperty();
      if (boundSql.hasAdditionalParameter(propertyName)) {
        value = boundSql.getAdditionalParameter(propertyName);
      } else if (parameterObject == null) {
        value = null;
      } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
        value = parameterObject;
      } else {
        MetaObject metaObject = configuration.newMetaObject(parameterObject);
        value = metaObject.getValue(propertyName);
      }
      cacheKey.update(value);
    }
  }
  if (configuration.getEnvironment() != null) {
    // issue #176
    cacheKey.update(configuration.getEnvironment().getId());
  }
  return cacheKey;
}
```

#### MyBatis使用Redis缓存

MyBatis除了提供内置的一级缓存和二级缓存外，还支持使用第三方缓存（例如Redis、Ehcache）作为二级缓存。MyBatis官方提供了一个mybatis-redis模块，该模块用于整合Redis作为二级缓存。

### 反射模块源码分析

#### ORM框架查询数据步骤

1. 从数据库加载对象
2. 找到映射匹配规则
3. 实例化目标对象
4. 对象属性赋值

#### MyBatis实现的流程

1. 使用Reflector读取类元信息
2. 使用ObjectWrapper读取对象信息，并对对象属性进行赋值操作

#### 反射的核心类

- ObjectFactory：MyBatis每次创建对象的新实例时，都会使用对象工厂去创建POJO
- ReflectorFactory：创建Reflector的工厂类，Reflector是MyBatis反射模块的基础，每个Reflector都对应一个类，在其中缓存了反射操作所需要的类元信息
- ObjectWrapper：对对象的包装，抽象了对象的属性信息，他定于了一系列查询对象的方法，以及更新属性的方式
- ObjectWrapperFactory：ObjectWrapper的工厂类，用于创建ObjectWrapper
- MetaObject：封装了对象元信息，包装了MyBatis的五个核心的反射类，也是提供外部使用的反射工具类，可以利用它读取或修改对象的属性信息

### Binding模块源码分析

核心类：

- MapperRegistry：mapper接口和对应的代理对象工厂的注册中心

- MapperProxyFactory：用于生成mapper接口动态代理的实例对象，每次创建都会创建新的MapperProxy对象。

  ```java
  protected T newInstance(MapperProxy<T> mapperProxy) {
      // 创建实现了mapper接口的动态代理对象
      return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }
  ```

- MapperProxy：实现了InvocationHandler接口，增强mapper接口的实现

- MapperMethod：封装了mapper接口中对对应方法的信息，一级对应的sql语句信息，它是mapper接口和映射配置文件中sql语句的桥梁

分析源码入口：XMLMapperBuilder.bindMapperForNamespace()

## 动态SQL实现原理

### 动态sql元素

- 单条件分支判断：if
- 多条件分支判断choose、when、otherwise，与if标签不同的是，所有的when标签和otherwise标签是互斥的，当任何一个when标签满足条件时，其他标签均视为条件不成立。
- 辅助元素：
    - trim
    - where：where标签用于保证至少有一个查询条件时，才会在SQL语句中追加WHERE关键字，同时能够剔除WHERE关键字后相邻的OR和AND关键字
    - set
- 循环语句：forEach

### 关键组件

- SqlSource：SqlSource用于描述SQL资源，SqlSource接口有4个不同的实现，分别为StaticSqlSource、DynamicSqlSource、RawSqlSource和ProviderSqlSource。
- BoundSql：BoundSql是对动态sql解析生成之后的sql语句和参数映射信息的封装

### 实现原理

SqlSource用于描述MyBatis中的SQL资源信息，LanguageDriver用于解析SQL配置，将SQL配置信息转换为SqlSource对象，SqlNode用于描述动态SQL中if、where等标签信息，LanguageDriver解析SQL配置时，会把if、where等动态SQL标签转换为SqlNode对象，封装在SqlSource中。而解析后的SqlSource对象会作为MappedStatement对象的属性保存在MappedStatement对象中。执行Mapper时，会根据传入的参数信息调用SqlSource对象的getBoundSql()方法获取BoundSql对象，这个过程就完成了将SqlNode对象转换为SQL语句的过程。

------

预编译 #{}：将传入的参数都当作一个字符串，会对自动传入的参数加一个单引号，能够很大程度防止sql注入，预编译可以粗暴的理解为就是加单引号和不加单引号的区别

传值 ${}：传入的参数直接显示生成到sql中，无法防止sql注入

表名，选取的列是动态的，order by和in操作，可以考虑使用${}

## MyBatis插件原理
插件时用来改变或者扩展mybatis原有的功能，通过实现Interceptor接口

MyBatis在四大对象的创建过程中，都会有插件进行介入。插件可以利用动态代理机制一层层的包装目标对象，而实现在目标对象执行目标方法之前进行拦截的效果。

MyBatis 允许在已映射语句执行过程中的某一点进行拦截调用。
默认情况下，MyBatis 允许使用插件来拦截的方法调用包括：

- Executor(update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
- ParameterHandler(getParameterObject, setParameters)
- ResultSetHandler(handleResultSets, handleOutputParameters)
- StatementHandler(prepare, parameterize, batch, update, query)

#### 用到的设计模式

- 责任链模式

## 其它

### MyBatis批量操作的方式

- 通过forEach动态拼装语句
- 通过BATCH类型的execute

------

编写代码时参数个数：5个以上用实体来封装

用mybatis代码生成器的Example的弊端：

1. 代码耦合，加入数据库字段修改，修改面积很大，如果用resultMap则方便很多
2. 无法控制代码顺序，可能导致索引失效

插入返回iduseGeneratedKeys="true" keyProperty="id"

动态代理：implements InvocationHandler

反射性能的问题可以忽略不计

Java的连接池和数据库的连接池是有关联的

暴力反射，不考虑方法访问修饰符的作用范围 public、private，没有get、set方法也会生成

先调用二级缓存再一级缓存。

SqlSessionFactory中维护了一个数据库连接池

预编译可以粗暴的理解为就是加单引号和不加单引号的区别

SqlSessionFactory：工厂单例模式，存在于程序的整个生命周期

SqlSession：线程不安全，所以肯定不能定义成类级别

## Mybatis总结

对于ORM持久化框架之前一直是用的JDBC去连接数据库 ，对于JDBC来连接库来说可能存在一些不足， 那么MyBatis确切的说只能算半持久化框架，因为MyBatis是需要我们去自动的编写我们的SQL语句的， 我们可以用JDBC&MyBatis做一些比较

我们在使用JDBC的时候会对数据库进行一些频繁创建连接和释放连接的操作从而影响的整个系统的性能。那么针对这一方面我们的MyBatis很好的利用了数据库连接池来对我们的链接进行的管理，例如我们在SQLMapConfig.xml的配置文件中使用连接池的技术很好的解决了这一问题。那么连接池的原理就是为了提高性能而生产出来的，第一次连接数据库时会创建一个连接然后请求结束后那么连接并不会去关闭，而是放入了连接池中等待下一次的连接，当有请求来时直接使用上一次的连接进行对Server的访问 那么这样就省略了创建连接和销毁连接从而从一定程度上提高了程序的性能。

回顾之前学习JDBC技术的时候，我们使用JDBC连接数据库SQL语句使用StringBuffer和StringBuilder或者是String进行拼接的。不利于我们对代码的维护，我们都知道在实际的应用中很多的时候都会变动 SQL语句。

MyBatis将我们的SQL语句放入到Mapper映射文件中动态的拼写SQL，从一定程度上来说可以提高性能，并且提高了代码的可扩展性的问题。




