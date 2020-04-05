[TOC]
## 概述

### Mybatis快速入门

SqlSessionFactoryBuilder：读取配置信息创建SqlSessionFactory，建造者模式，方法级别生命周期

SqlSessionFactory：创建SqlSession，工厂单例模式，存在于程序的整个生命周期

SqlSession：代表一次数据库连接，可以直接发送SQL执行，也可以调用mapper访问数据库；线程不安全，要保证线程独享（方法级）

SQL Mapper：由一个Java接口和XML文件组成，包含了要执行的SQL语句和结果映射规则，方法级别生命周期

https://www.jianshu.com/p/2756e81d02ff

https://mybatis.org/mybatis-3/zh/index.html

#### sql参数（向sql语句传递可变参数）

参数个数：5个以上用实体

预编译 #{}：将传入的参数都当作一个字符串，会对自动传入的参数加一个单引号，能够很大程度防止sql注入

传值${}：传入的参数直接显示生成到sql中，无法防止sql注入

表名，选取的列是动态的，order by和in操作，可以考虑使用${}

## 进阶

### MyBatis的配置

### mapper的配置

- 基于xml的配置

- 基于注解的配置：

  注解方式的（@Insert,@Select）不推介用，可读性差

#### 批量操作

- 通过forEach动态拼装语句
- 通过BATCH类型的execute

### 动态SQL

#### 动态sql元素

- 单条件分支判断：if
- 多条件分支判断：choose、when、otherwise
- 辅助元素：trim、where、set
- 循环语句：forEach

### 缓存

- 一级缓存

  一级缓存默认开启，想要关闭可以在select标签配置flushCache = "true"

  一级缓存存在于sqlSession的生命周期内

- 二级缓存

  存在于SqlSessionFactory的生命周期中，可以理解为跨sqlSession，缓存是以namespace为单位

  setting参数cacheEnabled，这个参数是二级缓存的全局开关

  要使用还需要配置

  ```<cache eviction = "FIFO" flushInterval="60000" size = "512" readonly="true">```

  二级缓存容易出现脏读，不建议使用

  ​	在两个namespace下都有同一份数据的副本，其中一个namespace对数据修改之后，另一个namespace就	会出现脏读（关联查询容易出现这个问题）

## 高级

### 源码分析

#### 源码导入过程：

1. 下载源码：https://github.com/mybatis/mybatis-3
2. mybatis的源码是maven工程，在编辑器进行导入
3. 把mybatis源码的pom文件<optional>true</optional>,全部改为false
4. 在工程目录下执行 mvn clean install -Dmaven.test.skip=true 将当前工程安装到本地仓库
5. 其它工程依赖此工程

mvn clean source:jar install -Dmaven.test.skip=true 将**源码**安装到本地仓库

#### mybatis的整体架构

接口层：SqlSession

核心处理层：配置解析、参数映射、SQL解析、SQL执行、结果集映射、插件

基础支撑层：数据源、事务管理、缓存、Binding模块、反射、类型转换、日志模块、资源加载、解析器

#### 设计模式的几个原则

- 单一职责原则
- 依赖倒置原则：高层模块不直接依赖于低层模块的具体实现，解藕高层与低层，即面向接口编程
- 开放-封闭原则

#### 日志模块分析

适配器模式

代理模式

#### 数据源模块分析

常见的数据源组件都实现了javax.sql.DataSource接口

MyBatis不但可以集成第三方的数据源组件，自己也实现了数据源的实现

核心类：

1. PooledConnection：使用动态代理封装了真正的数据库连接对象
2. PoolState：用于管理PooledConnection对象状态的组件，通过两个list分别管理空闲状态的连接资源和活跃状态的连接资源
3. PooledDataSource：一个简单的，同步的，线程安全的数据库连接池

#### 缓存模块分析

MyBatis的缓存是基于map实现的，从缓存里面读取数据是缓存模块的核心基础功能

除了核心功能之外，有很多的附加功能，如：防止缓存击穿，添加缓存清空策略、序列化能力、日志能力、定时清空能力，附加功能可以以任意的组合附加到核心功能之上

为核心功能添加附加功能的方式：

- 继承：弊端是继承的方式是静态的，新功能可能存在多种组合的时候还会出现多个子类
- 装饰器模式：用于代替继承的技术，无需通过继承增加子类就能扩展对象的新功能。使用对象的关联关系代替继承关系，更加灵活，同时避免类型体系的快速膨胀

缓存模块用到了装饰器模式

核心模块：

- Cache：Cache接口是缓存模块的核心接口，定义了缓存模块的基本操作

- PerpetualCache：在缓存模块中扮演ConcreteComponent角色，使用HashMap来实现cache的相关操作

- BlockingCache：阻塞版本的缓存装饰器，保证只有一个线程到数据库去查找指定key对应的数据

- CacheKey：MyBatis涉及到动态SQL的原因，缓存项的key不能仅仅通过一个String来表示，所以通过CacheKey来封装key值，CacheKey可以封装多个影响缓存key的因素

  构成CacheKey的对象：

  1. mapperStatement的id
  2. 指定查询集的范围（分页信息）
  3. 查询所使用的SQL语句
  4. 用户传递给SQL语句的实际参数值

锁的粒度：

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

#### 反射模块分析

ORM框架查询数据步骤：

1. 从数据库加载对象
2. 找到映射匹配规则
3. 实例化目标对象
4. 对象属性赋值

MyBatis实现的流程：

1. 使用Reflector读取类元信息
2. 使用ObjectWrapper读取对象信息，并对对象属性进行赋值操作

反射的核心类：

- ObjectFactory：MyBatis每次创建对象的新实例时，都会使用对象工厂去创建POJO
- ReflectorFactory：创建Reflector的工厂类，Reflector是MyBatis反射模块的基础，每个Reflector都对应一个类，在其中缓存了反射操作所需要的类元信息
- ObjectWrapper：对对象的包装，抽象了对象的属性信息，他定于了一系列查询对象的方法，以及更新属性的方式
- ObjectWrapperFactory：ObjectWrapper的工厂类，用于创建ObjectWrapper
- MetaObject：封装了对象元信息，包装了MyBatis的五个核心的反射类，也是提供外部使用的反射工具类，可以利用它读取或修改对象的属性信息

#### MyBatis启动流程源码分析

```java
// 源码分析的入口
@Before
public void init() throws IOException {
    String resource = "mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    // 1.读取mybatis配置文件创建 sqlSessionFactory
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
}
```

MyBatis的核心流程：

1. 读取XML配置文件和注解中的配置信息，创建配置对象，并完成各个模块的初始化工作
2. 封装iBatis的编程模型，使用mapper接口开发的初始化工作
3. 通过SqlSession完成SQL的解析，参数的映射，SQL的执行，结果的解析过程

建造者模式，链式编程

MyBatis的初始化，就是建立一个Configuration对象

Configuration：

- XMLConfigBuilder：主要负责解析mybatis-config.xml

- XMLMapperBuilder：主要负责解析映射配置文件

- XMLStatementBuilder：主要负责解析映射配置文件中的SQL节点

这三个Builder看上去不是流式风格，不像建造者模式，但是用到了建造者模式的设计思想，核心目的都是为了创建Configuration对象

config对象加载到内存，在内存中各个配置都有一个实体进行对应

MappedStatment：解析SQL语句

映射器的核心类：

- Configuration：MyBatis启动初始化的核心就是将所有xml配置文件信息加载到Configuration对象中，Configuration是单例的，生命周期是应用级的
- MapperRegistry：mapper接口动态代理工厂类的注册中心。在MyBatis中，通过mapperProxy实现InvocationHandler接口，MapperProxyFactory用于生成动态代理的实例对象
- ResultMap：用于解析mapper.xml中的ResultMap节点，使用ResultMapping来封装id、result等子元素
- MappedStatment：用于存储mapper.xml文件中的select、insert、update和delete语句，同时还包含了这些节点的很多重要属性
- SqlSource：mapper.xml的sql语句会被解析成SqlSource对象，经过解析SqlSource包含的语句最终仅包含？占位符，可以直接提交给数据库执行

#### Binding模块源码分析

核心类：

- MapperRegistry：mapper接口和对应的代理对象工厂的注册中心
- MapperProxyFactory：用于生成mapper接口动态代理的实例对象
- MapperProxy：实现了InvocationHandler接口，增强mapper接口的实现
- MapperMethod：封装了mapper接口中对对应方法的信息，一级对应的sql语句信息，它是mapper接口和映射配置文件中sql语句的桥梁

分析源码入口：XMLMapperBuilder.bindMapperForNamespace()

#### 创建SqlSession源码分析

用到了策略模式

SqlSession是MyBatis对外提供的最关键的接口，通过它可以执行数据库读写命令、获取映射器、管理事务等

------

Executor组件分析：

Executor是MyBatis核心接口之一，定义了数据库操作最基本的方法，SqlSession的功能都是基于它来实现的。

Executor的三个实现类解读：

- SimpleExecutor是最简单的执行器，根据对应的sql直接执行即可，不会做一些额外的操作；
- BatchExecutor执行器，顾名思义，通过批量操作来优化性能。通常需要注意的是批量更新操作，由于内部有缓存的实现，使用完成后记得调用`flushStatements`来清除缓存。
- ReuseExecutor 可重用的执行器，重用的对象是Statement，也就是说该执行器会缓存同一个sql的Statement，省去Statement的重新创建，优化性能。内部的实现是通过一个HashMap来维护Statement对象的。由于当前Map只在该session中有效，所以使用完成后记得调用`flushStatements`来清除Map。

SqlSession查询接口最终调用的是:

```java
<E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;
->
public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
->

```

StatementHandler组件分析：

StatementHandler完成MyBatis最核心的工作，也是Executor实现的基础；功能包括：创建statement对象、为sql语句绑定参数、执行增删改查等sql语句、将结果集进行转化

StatementHandler接口的实现大致有四个，其中三个实现类都是和JDBC中的Statement响对应的：

- SimpleStatementHandler，这个很简单了，就是对应我们JDBC中常用的Statement接口，用于简单SQL的处理；
- PreparedStatementHandler，这个对应JDBC中的PreparedStatement，预编译SQL的接口；
-  CallableStatementHandler，这个对应JDBC中CallableStatement，用于执行存储过程相关的接口；
- RoutingStatementHandler，这个接口是以上三个接口的路由，没有实际操作，只是负责上面三个StatementHandler的创建及调用。

### 插件

插件时用来改变或者扩展mybatis原有的功能，通过实现Interceptor接口

MyBatis在四大对象的创建过程中，都会有插件进行介入。插件可以利用动态代理机制一层层的包装目标对象，而实现在目标对象执行目标方法之前进行拦截的效果。

MyBatis 允许在已映射语句执行过程中的某一点进行拦截调用。

默认情况下，MyBatis 允许使用插件来拦截的方法调用包括：

- Executor(update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
- ParameterHandler(getParameterObject, setParameters)
- ResultSetHandler(handleResultSets, handleOutputParameters)
- StatementHandler(prepare, parameterize, batch, update, query)

插件的执行用到了**责任链模式**

## 其它

choose when 多个匹配到只有一个生效

用mybatis代码生成器的Example的弊端：

1. 代码耦合，加入数据库字段修改，修改面积很大，如果用resultMap则方便很多
2. 无法控制代码顺序，可能导致索引失效

插入返回id

useGeneratedKeys="true" keyProperty="id"

动态代理：implements InvocationHandler

反射性能的问题可以忽略不计

Java的连接池和数据库的连接池是有关联的

暴力反射，不考虑方法访问修饰符的作用范围 public、private，没有get、set方法也会生成

为什么mapper接口就能操作数据库？

​	配置文件的解读 + 动态代理的增强

git rm -r --cached target/

先调用二级缓存再一级缓存




