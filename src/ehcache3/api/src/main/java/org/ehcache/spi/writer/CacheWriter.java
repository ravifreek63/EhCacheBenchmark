/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.spi.writer;

import java.util.Map;

import org.ehcache.exceptions.BulkCacheWriterException;

/**
 * A CacheWriter is associated with a given {@link org.ehcache.Cache} instance and will be used to maintain it in sync
 * with an underlying system of record during normal operations on the Cache.
 * <p>
 * Instances of this class have to be thread safe.
 * <p>
 * Any {@link java.lang.Exception} thrown by methods of this interface will be wrapped into a
 * {@link org.ehcache.exceptions.CacheWriterException} by the {@link org.ehcache.Cache} and will need to be handled by
 * the user.
 *
 * @see org.ehcache.exceptions.CacheWriterException
 * @author Alex Snaps
 */
public interface CacheWriter<K, V> {

  /**
   * Writes a single entry to the underlying system of record, maybe a brand new value or an update to an existing value
   *
   * @param key the key of the mapping being installed or updated
   * @param value the actual value being updated
   *
   * @see org.ehcache.Cache#put(Object, Object)
   */
  void write(K key, V value) throws Exception;

  /**
   * Writes multiple entries to the underlying system of record. These can either be new entries or updates to
   * existing ones. It is legal for this method to result in "partial success" where some subset of the entries
   * are written. In this case a {@link BulkCacheWriterException} must be thrown (see below)
   *
   * @param entries the key to value mappings
   * @throws BulkCacheWriterException This writer must throw this exception to indicate partial success. The exception
   * declares which keys were actually written (if any)
   * @throws Exception a generic failure. All entries will be considered not written in this case
   *
   * @see org.ehcache.Cache#putAll(java.util.Map)
   */
  void writeAll(Iterable<? extends Map.Entry<? extends K, ? extends V>> entries) throws BulkCacheWriterException, Exception;

  /**
   * Deletes a single entry from the underlying system of record.
   *
   * @param key the key to delete
   *
   * @see org.ehcache.Cache#remove(Object)
   */
  void delete(K key) throws Exception;

  /**
   * Deletes a set of entry from the underlying system of record. It is legal for this method to result in "partial success" where some subset of the keys
   * are deleted. In this case a {@link BulkCacheWriterException} must be thrown (see below)
   *
   * @param keys the keys to delete
   * @throws BulkCacheWriterException This writer must throw this exception to indicate partial success. The exception
   * declares which keys were actually deleted (if any)
   * @throws Exception a generic failure. All entries will be considered not deleted in this case
   *
   * @see org.ehcache.Cache#removeAll(java.util.Set)
   */
  void deleteAll(Iterable<? extends K> keys) throws BulkCacheWriterException, Exception;

}
