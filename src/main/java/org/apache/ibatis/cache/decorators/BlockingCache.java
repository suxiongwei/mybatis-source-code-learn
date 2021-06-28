/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * Simple blocking decorator
 *
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 * 阻塞版本的缓存装饰器，保证只有一个线程到数据库去查找指定key对应的数据
 * 使用了装饰器模式
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {
  /**
   * 阻塞的超时时长
   */
  private long timeout;
  /**
   * 被装饰的底层对象，一般是PerpetualCache
   */
  private final Cache delegate;
  /**
   * 锁对象集，粒度到key值
   * 细粒度的锁
   */
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 先获取锁，获取成功加锁，获取失败阻塞一段时间重试
    // 这样做的目的是防止多个线程一下子都怼到数据库，造成数据库的不可用，
    // 加了锁之后，如果缓存不存在，第一个限额线程先去数据库查询将值写到缓存，之后的线程直接从缓存取值
    acquireLock(key);
    Object value = delegate.getObject(key);
    // 获取数据成功，释放锁
    if (value != null) {
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private ReentrantLock getLockForKey(Object key) {
    // 把锁添加到locks集合中，如果添加成功使用新锁
    return locks.computeIfAbsent(key, k -> new ReentrantLock());
  }

  /**
   * 根据key值获取锁对象，获取锁成功加锁，获取锁失败阻塞一段时间重试
   * @param key
   */
  private void acquireLock(Object key) {
    // 获取锁对象
    Lock lock = getLockForKey(key);
    // 使用带超时时间的锁
    if (timeout > 0) {
      try {
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) { // 如果超时抛出异常
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else { // 使用不带超时时间的锁
      lock.lock();
    }
  }

  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    if (lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
