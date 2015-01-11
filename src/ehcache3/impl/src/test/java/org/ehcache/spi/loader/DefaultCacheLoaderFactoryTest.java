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

package org.ehcache.spi.loader;

import org.ehcache.CacheManager;
import org.ehcache.CacheManagerBuilder;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.CacheConfigurationBuilder;
import org.ehcache.config.DefaultConfiguration;
import org.ehcache.config.loader.DefaultCacheLoaderConfiguration;
import org.ehcache.config.loader.DefaultCacheLoaderFactoryConfiguration;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DefaultCacheLoaderFactoryTest {

  @Test
  public void testCacheConfigUsage() {
    final CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("foo",
            CacheConfigurationBuilder.newCacheConfigurationBuilder()
                .addServiceConfig(new DefaultCacheLoaderConfiguration(MyLoader.class))
                .buildConfig(Object.class, Object.class)).build();
    final Object foo = manager.getCache("foo", Object.class, Object.class).get(new Object());
    assertThat(foo, is(MyLoader.object));
  }

  @Test
  public void testCacheManagerConfigUsage() {

    final CacheConfiguration<Object, Object> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .buildConfig(Object.class, Object.class);

    final Map<String, CacheConfiguration<?, ?>> caches = new HashMap<String, CacheConfiguration<?, ?>>();
    caches.put("foo", cacheConfiguration);
    final DefaultConfiguration configuration = new DefaultConfiguration(caches, null, new DefaultCacheLoaderFactoryConfiguration()
        .addLoaderFor("foo", MyLoader.class));
    final CacheManager manager = CacheManagerBuilder.newCacheManager(configuration);
    final Object foo = manager.getCache("foo", Object.class, Object.class).get(new Object());
    assertThat(foo, is(MyLoader.object));
  }

  @Test
  public void testCacheConfigOverridesCacheManagerConfig() {
    final CacheConfiguration<Object, Object> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .addServiceConfig(new DefaultCacheLoaderConfiguration(MyOtherLoader.class))
        .buildConfig(Object.class, Object.class);

    final Map<String, CacheConfiguration<?, ?>> caches = new HashMap<String, CacheConfiguration<?, ?>>();
    caches.put("foo", cacheConfiguration);
    final DefaultConfiguration configuration = new DefaultConfiguration(caches, null, new DefaultCacheLoaderFactoryConfiguration()
        .addLoaderFor("foo", MyLoader.class));
    final CacheManager manager = CacheManagerBuilder.newCacheManager(configuration);
    final Object foo = manager.getCache("foo", Object.class, Object.class).get(new Object());
    assertThat(foo, is(MyOtherLoader.object));
  }

  public static class MyLoader implements CacheLoader<Object, Object> {

    private static final Object object = new Object() {
      @Override
      public String toString() {
        return MyLoader.class.getName() + "'s object";
      }
    };

    @Override
    public Object load(final Object key) throws Exception {
      return object;
    }

    @Override
    public Map<Object, Object> loadAll(final Iterable<?> keys) throws Exception {
      throw new UnsupportedOperationException("Implement me!");
    }
  }

  public static class MyOtherLoader extends MyLoader {

    private static final Object object = new Object() {
      @Override
      public String toString() {
        return MyOtherLoader.class.getName() + "'s object";
      }
    };

    @Override
    public Object load(final Object key) throws Exception {
      return object;
    }
  }
}