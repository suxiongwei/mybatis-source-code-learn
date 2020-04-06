# MyBatis源码学习

## 搭建MyBatis源码环境

1. 下载源码：https://github.com/mybatis/mybatis-3
2. mybatis的源码是maven工程，在编辑器进行导入
3. 把mybatis源码的pom文件```<optional>true</optional>```,全部改为false
4. 在工程目录下执行 ```mvn clean install -Dmaven.test.skip=true``` 将当前工程安装到本地仓库（```mvn clean source:jar install -Dmaven.test.skip=true``` 将**源码**也同时安装到本地仓库）
5. 其它工程依赖此工程

## MyBatis核心组件介绍

- Configuration：用于描述MyBatis的主配置信息，其他组件需要获取配置信息时，直接通过Configuration对象获取。除此之外，MyBatis在应用启动时，将Mapper配置信息、类型别名、TypeHandler等注册到Configuration组件中，其他组件需要这些信息时，也可以从Configuration对象中获取
- MappedStatement：MappedStatement用于描述Mapper中的SQL配置信息，是对Mapper XML配置文件中<select|update|delete|insert>等标签或者@Select/@Update等注解配置信息的封装。
- SqlSession：SqlSession是MyBatis提供的面向用户的API，表示和数据库交互时的会话对象，用于完成数据库的增删改查功能。SqlSession是Executor组件的外观，目的是对外提供易于理解和使用的数据库操作接口。
- Executor：Executor是MyBatis的SQL执行器，MyBatis中对数据库所有的增删改查操作都是由Executor组件完成的。
- StatementHandler：StatementHandler封装了对JDBC Statement对象的操作，比如为Statement对象设置参数，调用Statement接口提供的方法与数据库交互，等等。
- ParameterHandler：当MyBatis框架使用的Statement类型为CallableStatement和PreparedStatement时，ParameterHandler用于为Statement对象参数占位符设置值。
- ResultSetHandler：ResultSetHandler封装了对JDBC中的ResultSet对象操作，当执行SQL类型为SELECT语句时，ResultSetHandler用于将查询结果转换成Java对象。
- TypeHandler：TypeHandler是MyBatis中的类型处理器，用于处理Java类型与JDBC类型之间的映射。它的作用主要体现在能够根据Java类型调用PreparedStatement或CallableStatement对象对应的setXXX()方法为Statement对象设置值，而且能够根据Java类型调用ResultSet对象对应的getXXX()获取SQL执行结果。

## mybatis的整体架构

接口层：SqlSession

核心处理层：配置解析、参数映射、SQL解析、SQL执行、结果集映射、插件

基础支撑层：数据源、事务管理、缓存、Binding模块、反射、类型转换、日志模块、资源加载、解析器

## MyBatis的核心流程

1. 读取XML配置文件和注解中的配置信息，创建配置对象，并完成各个模块的初始化工作
2. 封装iBatis的编程模型，使用mapper接口开发的初始化工作
3. 通过SqlSession完成SQL的解析，参数的映射，SQL的执行，结果的解析过程

## 设计模式的几个原则

- 单一职责原则
- 依赖倒置原则：高层模块不直接依赖于低层模块的具体实现，解藕高层与低层，即面向接口编程
- 开放-封闭原则

## 源码分析

### MyBatis启动流程源码分析

MyBatis框架启动时，会对所有的配置信息进行解析，然后将解析后的内容注册到Configuration对象的这些属性中。

MyBatis的初始化，就是建立一个Configuration对象。Configuration对象加载到内存，在内存中各个配置都有一个实体进行对应。

#### 用到的设计模式：

- 工厂方法模式：Configuration组件作为Executor、StatementHandler、ResultSetHandler、ParameterHandler组件的工厂类，用于创建这些组件的实例。Configuration类中提供了这些组件的工厂方法，例如，Executor组件有4种不同的实现，分别为BatchExecutor、ReuseExecutor、SimpleExecutor、CachingExecutor，当defaultExecutorType的参数值为REUSE时，newExecutor()方法返回的是ReuseExecutor实例，当参数值为SIMPLE时，返回的是SimpleExecutor实例，这是典型的工厂方法模式的应用
- 建造者模式：XMLConfigBuilder、XMLMapperBuilder、XMLStatementBuilder这三个Builder看上去不是流式风格，不像建造者模式，但是用到了建造者模式的设计思想，核心目的都是为了创建Configuration对象。

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

映射器的核心类：

- Configuration：MyBatis启动初始化的核心就是将所有xml配置文件信息加载到Configuration对象中，Configuration是单例的，生命周期是应用级的

- MapperRegistry：mapper接口动态代理工厂类的注册中心。在MyBatis中，通过mapperProxy实现InvocationHandler接口，MapperProxyFactory用于生成动态代理的实例对象，MyBatis框架在应用启动时会解析所有的Mapper接口，然后调用MapperRegistry对象的addMapper()方法将Mapper接口信息和对应的MapperProxyFactory对象注册到MapperRegistry对象中。

- ResultMap：用于解析mapper.xml中的ResultMap节点，使用ResultMapping来封装id、result等子元素

- MappedStatment：用于存储mapper.xml文件中的select、insert、update和delete语句，同时还包含了这些节点的很多重要属性。

  SQL配置有两种方式：

  - 通过XML文件配置；
  - 是通过Java注解，注解方式的（@Insert,@Select）不推介用，可读性差。

- SqlSource：mapper.xml的sql语句会被解析成SqlSource对象，经过解析SqlSource包含的语句最终仅包含？占位符，可以直接提交给数据库执行

### SqlSession创建源码分析

SqlSession是MyBatis对外提供的最关键的接口，通过它可以执行数据库读写命令、获取映射器、管理事务等

#### 用到的设计模式：

- 策略模式：SqlSession中的策略模式体现在：创建数据源有3种方式，POOL、UNPOOL和JNDI。在创建Sqlsession的时候，是根据环境创建的，在Environment里面会指定数据源的方式，对于Sqlsession的使用代码来说，不管底层是如何创建Sqlsession的，都没有关系。只需要改配置，数据源模块就能够生产出3种不同类型的SqlSession。
- 外观模式：SqlSession是Executor组件的外观，目的是为用户提供更友好的数据库操作接口，这是设计模式中外观模式的典型应用。真正执行SQL操作的是Executor组件，Executor可以理解为SQL执行器。
- 装饰器模式：MyBatis支持一级缓存和二级缓存，当MyBatis开启了二级缓存功能时，会使用CachingExecutor对SimpleExecutor、ResueExecutor、BatchExecutor进行装饰，为查询操作增加二级缓存功能，这是装饰器模式的应用。
- 模板方法模式：BaseStatementHandler是一个抽象类，封装了通用的处理逻辑及方法执行流程，具体方法的实现由子类完成，这里使用到了设计模式中的模板方法模式。
- 工厂模式：MyBatis中的SqlSession实例使用工厂模式创建，所以在创建SqlSession实例之前需要先创建SqlSessionFactory工厂对象，然后调用SqlSessionFactory对象的openSession()方法
- 享元模式：ReuseExecutor 可重用的执行器，重用的对象是Statement，这是享元思想的应用
- 动态代理模式：MapperProxy使用的是JDK内置的动态代理，实现了InvocationHandler接口，invoke()方法中为通用的拦截逻辑
- 静态代理模式：RoutingStatementHandler使用静态代理模式，根据上下文决定生成哪一个具体实现类
- 建造者模式：SqlSessionFactoryBuilder：读取配置信息创建SqlSessionFactory，建造者模式，方法级别生命周期

#### Executor组件分析：

Executor是MyBatis核心接口之一，定义了数据库操作最基本的方法，SqlSession的功能都是基于它来实现的。

Executor的三个实现类解读：

- SimpleExecutor是最简单的执行器，根据对应的sql直接执行即可，不会做一些额外的操作；
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

5. 调用doQuery()方法进行查询，然后将查询结果进行缓存，doQuery()是一个模板方法，由BaseExecutor子类实现。

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

### 日志模块源码分析

#### 用到的设计模式：

- 适配器模式：在JAVA开发中，常用的日志框架有Log4j、Log4j2、java.util.logging、slf4j等，这些工具对外的接口不尽相同，为了统一这些工具的接口，MyBatis定义了一套统一的日志接口供上层使用。即用到了适配器模式。

- 代理模式：在日志模块的jdbc包下，包含很多个类，他们对JDBC的几个核心类进行的动态代理增强，变成了具备日志打印功能的类，我们看看StatementLogger，他是具备日志打印功能的Statement。
- 工厂模式：使用LogFactory获取Log实例

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

常见的数据源组件都实现了javax.sql.DataSource接口

MyBatis不但可以集成第三方的数据源组件，自己也实现了数据源的实现

核心类：

1. PooledConnection：使用动态代理封装了真正的数据库连接对象
2. PoolState：用于管理PooledConnection对象状态的组件，通过两个list分别管理空闲状态的连接资源和活跃状态的连接资源
3. PooledDataSource：一个简单的，同步的，线程安全的数据库连接池

### 缓存模块源码分析

MyBatis提供了一级缓存和二级缓存，其中一级缓存基于SqlSession实现，而二级缓存基于Mapper实现。MyBatis的缓存是基于map实现的，从缓存里面读取数据是缓存模块的核心基础功能。

- 一级缓存

  一级缓存默认开启，一级缓存存在于sqlSession的生命周期内

- 二级缓存

  存在于SqlSessionFactory的生命周期中，可以理解为跨sqlSession，缓存是以namespace为单位

  setting参数cacheEnabled，这个参数是二级缓存的全局开关

  要使用还需要配置

  ```<cache eviction = "FIFO" flushInterval="60000" size = "512" readonly="true">```

  二级缓存容易出现脏读，不建议使用

  在两个namespace下都有同一份数据的副本，其中一个namespace对数据修改之后，另一个namespace就	会出现脏读（关联查询容易出现这个问题）

除了核心功能之外，有很多的附加功能，如：防止缓存击穿，添加缓存清空策略、序列化能力、日志能力、定时清空能力，附加功能可以以任意的组合附加到核心功能之上。

#### 用到的设计模式：

- 装饰器模式：MyBatis的二级缓存通过装饰器模式实现的，当通过cacheEnabled参数开启了二级缓存，MyBatis框架会使用CachingExecutor对SimpleExecutor、ReuseExecutor或者BatchExecutor进行装饰

#### 核心模块：

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

#### MyBatis使用Redis缓存

MyBatis除了提供内置的一级缓存和二级缓存外，还支持使用第三方缓存（例如Redis、Ehcache）作为二级缓存。MyBatis官方提供了一个mybatis-redis模块，该模块用于整合Redis作为二级缓存。

### 反射模块源码分析

#### ORM框架查询数据步骤：

1. 从数据库加载对象
2. 找到映射匹配规则
3. 实例化目标对象
4. 对象属性赋值

#### MyBatis实现的流程：

1. 使用Reflector读取类元信息
2. 使用ObjectWrapper读取对象信息，并对对象属性进行赋值操作

#### 反射的核心类：

- ObjectFactory：MyBatis每次创建对象的新实例时，都会使用对象工厂去创建POJO
- ReflectorFactory：创建Reflector的工厂类，Reflector是MyBatis反射模块的基础，每个Reflector都对应一个类，在其中缓存了反射操作所需要的类元信息
- ObjectWrapper：对对象的包装，抽象了对象的属性信息，他定于了一系列查询对象的方法，以及更新属性的方式
- ObjectWrapperFactory：ObjectWrapper的工厂类，用于创建ObjectWrapper
- MetaObject：封装了对象元信息，包装了MyBatis的五个核心的反射类，也是提供外部使用的反射工具类，可以利用它读取或修改对象的属性信息

### Binding模块源码分析

核心类：

- MapperRegistry：mapper接口和对应的代理对象工厂的注册中心
- MapperProxyFactory：用于生成mapper接口动态代理的实例对象
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

预编译 #{}：将传入的参数都当作一个字符串，会对自动传入的参数加一个单引号，能够很大程度防止sql注入

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

#### 用到的设计模式：

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

先调用二级缓存再一级缓存

[TOC]