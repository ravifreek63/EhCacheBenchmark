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
package org.ehcache.config.xml;

import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.CacheConfigurationBuilder;
import org.ehcache.config.Configuration;
import org.ehcache.config.Eviction;
import org.ehcache.config.EvictionPrioritizer;
import org.ehcache.config.EvictionVeto;
import org.ehcache.config.loader.DefaultCacheLoaderConfiguration;
import org.ehcache.config.writer.DefaultCacheWriterConfiguration;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.expiry.Expiry;
import org.ehcache.internal.store.service.OnHeapStoreServiceConfig;
import org.ehcache.spi.loader.CacheLoader;
import org.ehcache.spi.service.ServiceConfiguration;
import org.ehcache.spi.writer.CacheWriter;
import org.ehcache.util.ClassLoading;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exposes {@link org.ehcache.config.Configuration} and {@link org.ehcache.config.CacheConfigurationBuilder} expressed
 * in a XML file that obeys the ehcache-core.xsd (todo link this to proper location, wherever this ends up being)
 * <p>
 * Instances of this class are not thread-safe
 *
 * @author Chris Dennis
 * @author Alex Snaps
 */
public class XmlConfiguration implements Configuration {

  private static final URL CORE_SCHEMA_URL = XmlConfiguration.class.getResource("/ehcache-core.xsd");

  private final URL xml;
  private final ClassLoader classLoader;
  private final Map<String, ClassLoader> cacheClassLoaders;

  private final Collection<ServiceConfiguration<?>> serviceConfigurations = new ArrayList<ServiceConfiguration<?>>();
  private final Map<String, CacheConfiguration<?, ?>> cacheConfigurations = new HashMap<String, CacheConfiguration<?, ?>>();
  private final Map<String, ConfigurationParser.CacheTemplate> templates = new HashMap<String, ConfigurationParser.CacheTemplate>();

  /**
   * Constructs an instance of XmlConfiguration mapping to the XML file located at {@code url}
   * <p>
   * Parses the XML file at the {@code url} provided.
   *
   * @param url URL pointing to the XML file's location
   *
   * @throws IOException if anything went wrong accessing the URL
   * @throws SAXException if anything went wrong parsing or validating the XML
   * @throws ClassNotFoundException if a {@link java.lang.Class} declared in the XML couldn't be found
   * @throws InstantiationException if a user provided {@link java.lang.Class} couldn't get instantiated
   * @throws IllegalAccessException if a method (including constructor) couldn't be invoked on a user provided type
   */
  public XmlConfiguration(URL url)
      throws ClassNotFoundException, SAXException, InstantiationException, IllegalAccessException, IOException {
    this(url, null);
  }

  /**
   * Constructs an instance of XmlConfiguration mapping to the XML file located at {@code url} and using the provided
   * {@code classLoader} to load user types (e.g. key and value Class instances).
   * <p>
   * Parses the XML file at the {@code url} provided.
   *
   * @param url URL pointing to the XML file's location
   * @param classLoader ClassLoader to use to load user types.
   *
   * @throws IOException if anything went wrong accessing the URL
   * @throws SAXException if anything went wrong parsing or validating the XML
   * @throws ClassNotFoundException if a {@link java.lang.Class} declared in the XML couldn't be found
   * @throws InstantiationException if a user provided {@link java.lang.Class} couldn't get instantiated
   * @throws IllegalAccessException if a method (including constructor) couldn't be invoked on a user provided type
   */
  public XmlConfiguration(URL url, final ClassLoader classLoader)
      throws ClassNotFoundException, SAXException, InstantiationException, IOException, IllegalAccessException {
    this(url, classLoader, Collections.<String, ClassLoader>emptyMap());
  }

  /**
   * Constructs an instance of XmlConfiguration mapping to the XML file located at {@code url} and using the provided
   * {@code classLoader} to load user types (e.g. key and value Class instances). The {@code cacheClassLoaders} will
   * let you specify a different {@link java.lang.ClassLoader} to use for each {@link org.ehcache.Cache} managed by
   * the {@link org.ehcache.CacheManager} configured using this {@link org.ehcache.config.xml.XmlConfiguration}
   * <p>
   * Parses the XML file at the {@code url} provided.
   *
   * @param url URL pointing to the XML file's location
   * @param classLoader ClassLoader to use to load user types.
   *
   * @throws IOException if anything went wrong accessing the URL
   * @throws SAXException if anything went wrong parsing or validating the XML
   * @throws ClassNotFoundException if a {@link java.lang.Class} declared in the XML couldn't be found
   * @throws InstantiationException if a user provided {@link java.lang.Class} couldn't get instantiated
   * @throws IllegalAccessException if a method (including constructor) couldn't be invoked on a user provided type
   */
  public XmlConfiguration(URL url, final ClassLoader classLoader, final Map<String, ClassLoader> cacheClassLoaders)
      throws ClassNotFoundException, SAXException, InstantiationException, IllegalAccessException, IOException {
    this.xml = url;
    this.classLoader = classLoader;
    this.cacheClassLoaders = new HashMap<String, ClassLoader>(cacheClassLoaders);
    parseConfiguration();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void parseConfiguration()
      throws ClassNotFoundException, IOException, SAXException, InstantiationException, IllegalAccessException {
    ConfigurationParser configurationParser = new ConfigurationParser(xml.toExternalForm(), CORE_SCHEMA_URL);

    for (ServiceConfiguration<?> serviceConfiguration : configurationParser.getServiceConfigurations()) {
      serviceConfigurations.add(serviceConfiguration);
    }

    for (ConfigurationParser.CacheDefinition cacheDefinition : configurationParser.getCacheElements()) {
      CacheConfigurationBuilder<Object, Object> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder();
      String alias = cacheDefinition.id();

      ClassLoader cacheClassLoader = cacheClassLoaders.get(alias);
      if (cacheClassLoader != null) {
        builder = builder.withClassLoader(cacheClassLoader);
      }
      
      if (cacheClassLoader == null) {
        if (classLoader != null) {
          cacheClassLoader = classLoader;
        } else {
          cacheClassLoader = ClassLoading.getDefaultClassLoader();
        }
      }
      
      Class keyType = getClassForName(cacheDefinition.keyType(), cacheClassLoader);
      Class valueType = getClassForName(cacheDefinition.valueType(), cacheClassLoader);
      Long capacityConstraint = cacheDefinition.capacityConstraint();
      EvictionVeto evictionVeto = getInstanceOfName(cacheDefinition.evictionVeto(), cacheClassLoader, EvictionVeto.class);
      EvictionPrioritizer evictionPrioritizer = getInstanceOfName(cacheDefinition.evictionPrioritizer(), cacheClassLoader, EvictionPrioritizer.class, Eviction.Prioritizer.class);
      final ConfigurationParser.Expiry parsedExpiry = cacheDefinition.expiry();
      builder = builder.withExpiry(getExpiry(cacheClassLoader, parsedExpiry));
      for (ServiceConfiguration<?> serviceConfig : cacheDefinition.serviceConfigs()) {
        builder = builder.addServiceConfig(serviceConfig);
      }
      if (capacityConstraint != null) {
        builder = builder.maxEntriesInCache(capacityConstraint);
      }
      if(cacheDefinition.loader() != null) {
        final Class<CacheLoader<?, ?>> cacheLoaderClass = (Class<CacheLoader<?,?>>)getClassForName(cacheDefinition.loader(), cacheClassLoader);
        builder = builder.addServiceConfig(new DefaultCacheLoaderConfiguration(cacheLoaderClass));
      }
      if(cacheDefinition.writer() != null) {
        final Class<CacheWriter<?, ?>> cacheWriterClass = (Class<CacheWriter<?,?>>)getClassForName(cacheDefinition.writer(), cacheClassLoader);
        builder = builder.addServiceConfig(new DefaultCacheWriterConfiguration(cacheWriterClass));
      }
      final OnHeapStoreServiceConfig onHeapStoreServiceConfig = new OnHeapStoreServiceConfig();
      onHeapStoreServiceConfig.storeByValue(cacheDefinition.storeByValueOnHeap());
      builder.addServiceConfig(onHeapStoreServiceConfig);
      final CacheConfiguration<?, ?> config = builder.buildConfig(keyType, valueType, evictionVeto, evictionPrioritizer);
      cacheConfigurations.put(alias, config);
    }

    templates.putAll(configurationParser.getTemplates());
  }

  private Expiry<? super Object, ? super Object> getExpiry(ClassLoader cacheClassLoader, ConfigurationParser.Expiry parsedExpiry)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    final Expiry<? super Object, ? super Object> expiry;
    if (parsedExpiry.isUserDef()) {
      expiry = getInstanceOfName(parsedExpiry.type(), cacheClassLoader, Expiry.class);
    } else if (parsedExpiry.isTTL()) {
      expiry = Expirations.timeToLiveExpiration(new Duration(parsedExpiry.value(), parsedExpiry.unit()));
    } else if (parsedExpiry.isTTI()) {
      expiry = Expirations.timeToIdleExpiration(new Duration(parsedExpiry.value(), parsedExpiry.unit()));
    } else {
      expiry = Expirations.noExpiration();
    }
    return expiry;
  }

  private static <T> T getInstanceOfName(String name, ClassLoader classLoader, Class<T> type) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    if (name == null) {
      return null;
    }
    Class<?> klazz = getClassForName(name, classLoader);
    return klazz.asSubclass(type).newInstance();
  }

  @SuppressWarnings("unchecked")
  private static <T> T getInstanceOfName(String name, ClassLoader classLoader, Class<T> type, @SuppressWarnings("rawtypes") Class<? extends Enum> shortcutValues) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    if (name == null) {
      return null;
    }
    try {
      return (T) Enum.valueOf(shortcutValues, name);
    } catch (IllegalArgumentException iae) {
      return getInstanceOfName(name, classLoader, type);
    }
  }

  private static Class<?> getClassForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return Class.forName(name, true, classLoader);
  }

  /**
   * Exposes the URL where the XML file parsed or yet to be parsed was or will be sourced from.
   * @return The URL provided at object instantiation
   */
  public URL getURL() {
    return xml;
  }

  /**
   * Creates a new {@link org.ehcache.config.CacheConfigurationBuilder} seeded with the cache-template configuration
   * by the given {@code name} in the XML configuration parsed using {@link #parseConfiguration()}
   *
   * @param name the unique name identifying the cache-template element in the XML
   *
   * @return the preconfigured {@link org.ehcache.config.CacheConfigurationBuilder}
   *         or {@code null} if no cache-template for the provided {@code name}
   *
   * @throws ClassNotFoundException if a {@link java.lang.Class} declared in the XML couldn't be found
   * @throws InstantiationException if a user provided {@link java.lang.Class} couldn't get instantiated
   * @throws IllegalAccessException if a method (including constructor) couldn't be invoked on a user provided type
   */
  public CacheConfigurationBuilder<Object, Object> newCacheConfigurationBuilderFromTemplate(final String name) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    return newCacheConfigurationBuilderFromTemplate(name, null, null);
  }

  /**
   * Creates a new {@link org.ehcache.config.CacheConfigurationBuilder} seeded with the cache-template configuration
   * by the given {@code name} in the XML configuration parsed using {@link #parseConfiguration()}
   *
   * @param name the unique name identifying the cache-template element in the XML
   * @param keyType the type of keys for the {@link org.ehcache.config.CacheConfigurationBuilder} to use, would need to
   *                match the {@code key-type} declared in the template if declared in XML
   * @param valueType the type of values for the {@link org.ehcache.config.CacheConfigurationBuilder} to use, would need to
   *                  match the {@code value-type} declared in the template if declared in XML
   * @param <K> type of keys
   * @param <V> type of values
   *
   * @return the preconfigured {@link org.ehcache.config.CacheConfigurationBuilder}
   *         or {@code null} if no cache-template for the provided {@code name}
   *
   * @throws IllegalStateException if {@link #parseConfiguration()} hasn't yet been successfully invoked
   * @throws IllegalArgumentException if {@code keyType} or {@code valueType} don't match the declared type(s) of the template
   * @throws ClassNotFoundException if a {@link java.lang.Class} declared in the XML couldn't be found
   * @throws InstantiationException if a user provided {@link java.lang.Class} couldn't get instantiated
   * @throws IllegalAccessException if a method (including constructor) couldn't be invoked on a user provided type
   */
  @SuppressWarnings("unchecked")
  public <K, V> CacheConfigurationBuilder<K, V> newCacheConfigurationBuilderFromTemplate(final String name,
                                                                                         final Class<K> keyType,
                                                                                         final Class<V> valueType)
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {

    final ConfigurationParser.CacheTemplate cacheTemplate = templates.get(name);
    if (cacheTemplate == null) {
      return null;
    }

    if(keyType != null && cacheTemplate.keyType() != null && !keyType.getName().equals(cacheTemplate.keyType())) {
      throw new IllegalArgumentException("CacheTemplate '" + name + "' declares key type of " + cacheTemplate.keyType());
    }

    if(valueType != null && cacheTemplate.valueType() != null && !valueType.getName().equals(cacheTemplate.valueType())) {
      throw new IllegalArgumentException("CacheTemplate '" + name + "' declares value type of " + cacheTemplate.valueType());
    }

    CacheConfigurationBuilder<K, V> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder();
    if (cacheTemplate.capacityConstraint() != null) {
      builder = builder
          .maxEntriesInCache(cacheTemplate.capacityConstraint());
    }
    final ConfigurationParser.Expiry parsedExpiry = cacheTemplate.expiry();
    final ClassLoader defaultClassLoader = ClassLoading.getDefaultClassLoader();
    builder = builder
        .usingEvictionPrioritizer(getInstanceOfName(cacheTemplate.evictionPrioritizer(), defaultClassLoader, EvictionPrioritizer.class, Eviction.Prioritizer.class))
        .evitionVeto(getInstanceOfName(cacheTemplate.evictionVeto(), defaultClassLoader, EvictionVeto.class))
        .withExpiry(getExpiry(defaultClassLoader, parsedExpiry));
    for (ServiceConfiguration<?> serviceConfiguration : cacheTemplate.serviceConfigs()) {
      builder = builder.addServiceConfig(serviceConfiguration);
    }
    final OnHeapStoreServiceConfig onHeapStoreServiceConfig = new OnHeapStoreServiceConfig();
    onHeapStoreServiceConfig.storeByValue(cacheTemplate.storeByValueOnHeap());
    builder.addServiceConfig(onHeapStoreServiceConfig);
    return builder;
  }

  @Override
  public Map<String, CacheConfiguration<?, ?>> getCacheConfigurations() {
    return cacheConfigurations;
  }

  @Override
  public Collection<ServiceConfiguration<?>> getServiceConfigurations() {
    return serviceConfigurations;
  }

  @Override
  public ClassLoader getClassLoader() {
    return classLoader;
  }
}
